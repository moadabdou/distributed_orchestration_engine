package com.doe.manager.api.service;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.api.dto.*;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import com.doe.manager.workflow.WorkflowErrorCode;
import com.doe.manager.workflow.WorkflowException;
import com.doe.manager.workflow.WorkflowManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Application-layer service for the Workflow REST API.
 *
 * <p>Translates between request/response DTOs and the domain model,
 * delegates lifecycle mutations to {@link WorkflowManager}, and
 * serves read-heavy queries from the persisted {@link WorkflowRepository}.
 */
@Service
public class WorkflowService {

    private final WorkflowManager workflowManager;
    private final WorkflowRepository workflowRepository;
    private final JobRepository jobRepository;
    private final com.doe.manager.workflow.XComService xComService;
    private final long defaultJobTimeoutMs;

    public WorkflowService(
            WorkflowManager workflowManager,
            WorkflowRepository workflowRepository,
            JobRepository jobRepository,
            com.doe.manager.workflow.XComService xComService,
            @org.springframework.beans.factory.annotation.Value("${doe.workflow.default-job-timeout-ms:600000}") long defaultJobTimeoutMs) {
        this.workflowManager = workflowManager;
        this.workflowRepository = workflowRepository;
        this.jobRepository = jobRepository;
        this.xComService = xComService;
        this.defaultJobTimeoutMs = defaultJobTimeoutMs;
    }

    // ── Create ───────────────────────────────────────────────────────────────


    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest req) {
        validateCreateRequest(req);

        // Generate workflow ID upfront so we can link jobs to it
        UUID workflowId = UUID.randomUUID();

        // Build a label → Job map (preserving insertion order for dagIndex)
        Map<String, Job> labelToJob = new LinkedHashMap<>();
        List<JobDefinition> jobDefs = req.jobs();
        for (JobDefinition def : jobDefs) {
            // If type is not provided at top level, it might be in the payload (legacy)
            // But we prefer it from the definition
            Job job = Job.newJob(def.payload())
                    .workflowId(workflowId)
                    .timeoutMs(def.timeoutMs() > 0 ? def.timeoutMs() : defaultJobTimeoutMs)
                    .retryCount(def.retryCount() > 0 ? def.retryCount() : 0)
                    .jobLabel(def.label())
                    .build();
            labelToJob.put(def.label(), job);
        }

        // Resolve dependency edges (label → UUID)
        List<CreateWorkflowRequest.DependencyEdge> edges =
                req.dependencies() != null ? req.dependencies() : List.of();

        Map<String, List<UUID>> labelToDeps = new HashMap<>();
        for (CreateWorkflowRequest.DependencyEdge edge : edges) {
            Job from = labelToJob.get(edge.fromJobLabel());
            Job to   = labelToJob.get(edge.toJobLabel());
            if (from == null) {
                throw new WorkflowException(WorkflowErrorCode.MISSING_DEPENDENCY,
                        "Dependency references unknown job label: " + edge.fromJobLabel());
            }
            if (to == null) {
                throw new WorkflowException(WorkflowErrorCode.MISSING_DEPENDENCY,
                        "Dependency references unknown job label: " + edge.toJobLabel());
            }
            // toJobLabel depends on fromJobLabel, so fromJobLabel is a prerequisite of toJobLabel
            labelToDeps.computeIfAbsent(edge.toJobLabel(), k -> new ArrayList<>())
                       .add(from.getId());
        }

        // Build WorkflowJobs
        Workflow.Builder builder = Workflow.newWorkflow(req.name()).id(workflowId);
        int idx = 0;
        for (Map.Entry<String, Job> entry : labelToJob.entrySet()) {
            String label = entry.getKey();
            Job job = entry.getValue();
            List<UUID> deps = labelToDeps.getOrDefault(label, List.of());
            WorkflowJob wj = WorkflowJob.fromJob(job)
                    .dagIndex(idx++)
                    .dependencies(deps)
                    .build();
            builder.addJob(wj);
        }

        Workflow workflow = builder.build();
        Workflow registered = workflowManager.registerWorkflow(workflow);

        // Retrieve the persisted entity for the response (WorkflowPersistenceListener handles DB write)
        return toResponse(registered);
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WorkflowSummaryResponse> listWorkflows(int page, int size, WorkflowStatus status) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WorkflowEntity> entityPage = (status != null)
                ? workflowRepository.findByStatus(status, pageable)
                : workflowRepository.findAll(pageable);
        return entityPage.map(e -> new WorkflowSummaryResponse(
                e.getId(),
                e.getName(),
                e.getStatus(),
                jobRepository.countByWorkflowId(e.getId()),
                e.getCreatedAt()
        ));
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(UUID id) {
        Workflow w = workflowManager.getWorkflow(id);
        if (w != null) {
            return toResponse(w);
        }
        // Fall back to DB when not in memory (e.g. COMPLETED/FAILED old workflows)
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowException(WorkflowErrorCode.WORKFLOW_NOT_FOUND,
                        "Workflow not found: " + id));
        return toResponseFromEntity(entity);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public WorkflowResponse updateWorkflow(UUID id, UpdateWorkflowRequest req) {
        if (req.jobs() == null || req.jobs().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one job");
        }
        // Reuse create logic to rebuild the domain model, then delegate to manager
        CreateWorkflowRequest asCreate = new CreateWorkflowRequest(
                req.name(), req.jobs(), req.dependencies());
        Workflow rebuilt = buildWorkflowDomain(id, asCreate);
        Workflow updated = workflowManager.updateWorkflow(id, rebuilt);
        return toResponse(updated);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void deleteWorkflow(UUID id) {
        workflowManager.deleteWorkflow(id);
    }

    // ── XCom ─────────────────────────────────────────────────────────────────

    @Transactional
    public void clearXComHistory(UUID id) {
        // 1. Check if workflow is in memory
        Workflow w = workflowManager.getWorkflow(id);
        WorkflowStatus status;

        if (w != null) {
            status = w.getStatus();
        } else {
            // 2. Fall back to DB
            WorkflowEntity entity = workflowRepository.findById(id)
                    .orElseThrow(() -> new WorkflowException(WorkflowErrorCode.WORKFLOW_NOT_FOUND,
                            "Workflow not found: " + id));
            status = entity.getStatus();
        }

        // 3. Refuse if RUNNING
        if (status == WorkflowStatus.RUNNING) {
            throw new WorkflowException(WorkflowErrorCode.WORKFLOW_RUNNING,
                    "Cannot clear XCom history while the workflow is RUNNING. Pause or stop it first.");
        }

        // 4. Delegate to XComService
        xComService.deleteXComsByWorkflowId(id);
    }

    // ── Lifecycle controls ───────────────────────────────────────────────────


    @Transactional
    public WorkflowResponse executeWorkflow(UUID id) {
        return toResponse(workflowManager.executeWorkflow(id));
    }

    @Transactional
    public WorkflowResponse pauseWorkflow(UUID id) {
        return toResponse(workflowManager.pauseWorkflow(id));
    }

    @Transactional
    public WorkflowResponse resumeWorkflow(UUID id) {
        return toResponse(workflowManager.resumeWorkflow(id));
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.doe.manager.server.ManagerServer managerServer;

    @Transactional
    public WorkflowResponse resetWorkflow(UUID id) {
        Workflow workflow = workflowManager.getWorkflow(id);
        if (workflow != null) {
            for (WorkflowJob wj : workflow.getJobs()) {
                Job job = wj.getJob();
                if (job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.ASSIGNED) {
                    if (managerServer != null) {
                        managerServer.getJobRegistry().get(job.getId()).ifPresent(regJob -> {
                            UUID workerId = regJob.getAssignedWorkerId();
                            if (workerId != null) {
                                managerServer.sendCancelJob(workerId, job.getId());
                            }
                        });
                        // Fallback check
                        JobEntity entity = jobRepository.findById(job.getId()).orElse(null);
                        if (entity != null && entity.getWorkerId() != null) {
                            managerServer.sendCancelJob(entity.getWorkerId(), job.getId());
                        }
                    }
                }
            }
        }
        return toResponse(workflowManager.resetWorkflow(id));
    }

    // ── DAG ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DagGraphResponse getDag(UUID id) {
        Workflow w = workflowManager.getWorkflow(id);
        if (w == null) {
            throw new WorkflowException(WorkflowErrorCode.WORKFLOW_NOT_FOUND,
                    "Workflow not found: " + id);
        }

        List<DagGraphResponse.DagNodeResponse> nodes = w.getJobs().stream()
                .map(wj -> {
                    Job job = wj.getJob();
                    // Enrich from DB for status/result/workerId
                    JobEntity entity = jobRepository.findById(job.getId()).orElse(null);
                    return new DagGraphResponse.DagNodeResponse(
                            job.getId(),
                            job.getJobLabel() != null ? job.getJobLabel() : "job-" + wj.getDagIndex(),
                            wj.getDagIndex(),
                            entity != null ? entity.getStatus() : job.getStatus(),
                            job.getPayload(),
                            entity != null ? entity.getResult() : null,
                            entity != null ? entity.getWorkerId() : null,
                            job.getTimeoutMs(),
                            job.getJobLabel(),
                            job.getCreatedAt(),
                            job.getUpdatedAt()
                    );
                })
                .collect(Collectors.toList());

        List<DagGraphResponse.DagEdgeResponse> edges = w.getJobs().stream()
                .flatMap(wj -> wj.getDependencies().stream()
                        .map(depId -> new DagGraphResponse.DagEdgeResponse(depId, wj.getJob().getId())))
                .collect(Collectors.toList());

        return new DagGraphResponse(w.getId(), w.getName(), w.getStatus(), nodes, edges);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void validateCreateRequest(CreateWorkflowRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Workflow name must not be blank");
        }
        if (req.jobs() == null || req.jobs().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one job");
        }
        // Check all labels are unique
        Set<String> seen = new HashSet<>();
        for (JobDefinition def : req.jobs()) {
            if (def.label() == null || def.label().isBlank()) {
                throw new IllegalArgumentException("Each job must have a non-blank label");
            }
            if (!seen.add(def.label())) {
                throw new IllegalArgumentException("Duplicate job label: " + def.label());
            }
            if (def.payload() == null || def.payload().isBlank()) {
                throw new IllegalArgumentException("Job payload must not be blank: " + def.label());
            }
        }
    }

    /**
     * Builds a domain Workflow from a create request, using the given UUID as the workflow ID.
     * Used by both createWorkflow (generates a new ID) and updateWorkflow (reuses existing ID).
     */
    private Workflow buildWorkflowDomain(UUID workflowId, CreateWorkflowRequest req) {
        Map<String, Job> labelToJob = new LinkedHashMap<>();
        for (JobDefinition def : req.jobs()) {
            Job job = Job.newJob(def.payload())
                    .workflowId(workflowId)
                    .timeoutMs(def.timeoutMs() > 0 ? def.timeoutMs() : defaultJobTimeoutMs)
                    .retryCount(def.retryCount() > 0 ? def.retryCount() : 0)
                    .jobLabel(def.label())
                    .build();
            labelToJob.put(def.label(), job);
        }

        List<CreateWorkflowRequest.DependencyEdge> edges =
                req.dependencies() != null ? req.dependencies() : List.of();
        Map<String, List<UUID>> labelToDeps = new HashMap<>();
        for (CreateWorkflowRequest.DependencyEdge edge : edges) {
            Job from = labelToJob.get(edge.fromJobLabel());
            Job to   = labelToJob.get(edge.toJobLabel());
            if (from == null || to == null) {
                throw new WorkflowException(WorkflowErrorCode.MISSING_DEPENDENCY,
                        "Dependency references unknown job label: " + edge.fromJobLabel() + " -> " + edge.toJobLabel());
            }
            labelToDeps.computeIfAbsent(edge.toJobLabel(), k -> new ArrayList<>())
                       .add(from.getId());
        }

        Workflow.Builder builder = Workflow.newWorkflow(req.name()).id(workflowId);
        int idx = 0;
        for (Map.Entry<String, Job> entry : labelToJob.entrySet()) {
            String label = entry.getKey();
            Job job = entry.getValue();
            List<UUID> deps = labelToDeps.getOrDefault(label, List.of());
            builder.addJob(WorkflowJob.fromJob(job).dagIndex(idx++).dependencies(deps).build());
        }
        return builder.build();
    }

    private WorkflowResponse toResponse(Workflow w) {
        List<WorkflowJob> jobs = w.getJobs();
        int total     = jobs.size();
        int completed = (int) jobs.stream().filter(wj -> wj.getJob().getStatus() == JobStatus.COMPLETED).count();
        int failed    = (int) jobs.stream().filter(wj -> wj.getJob().getStatus() == JobStatus.FAILED).count();
        int pending   = (int) jobs.stream().filter(wj -> wj.getJob().getStatus() == JobStatus.PENDING).count();

        // updatedAt = most recent job updatedAt, or workflow createdAt if no jobs
        java.time.Instant updatedAt = jobs.stream()
                .map(wj -> wj.getJob().getUpdatedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(w.getCreatedAt());

        return new WorkflowResponse(
                w.getId(), w.getName(), w.getStatus(),
                total, completed, failed, pending,
                w.getCreatedAt(), updatedAt);
    }

    private WorkflowResponse toResponseFromEntity(WorkflowEntity e) {
        int total = jobRepository.countByWorkflowId(e.getId());
        return new WorkflowResponse(
                e.getId(), e.getName(), e.getStatus(),
                total, 0, 0, 0,
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
