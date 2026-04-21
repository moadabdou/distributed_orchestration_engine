package com.doe.manager.api.controller;

import com.doe.core.model.WorkflowStatus;
import com.doe.manager.api.dto.*;
import com.doe.manager.api.service.JobService;
import com.doe.manager.api.service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for workflow lifecycle management.
 *
 * <p>Base path: {@code /api/v1/workflows}
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final JobService jobService;

    public WorkflowController(WorkflowService workflowService, JobService jobService) {
        this.workflowService = workflowService;
        this.jobService = jobService;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /** POST /workflows — Create a workflow */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createWorkflow(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    /** GET /workflows — Paginated list, optional status filter */
    @GetMapping
    public Page<WorkflowSummaryResponse> listWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) WorkflowStatus status) {
        return workflowService.listWorkflows(page, size, status);
    }

    /** GET /workflows/{id} — Get workflow detail */
    @GetMapping("/{id}")
    public WorkflowResponse getWorkflow(@PathVariable("id") UUID id) {
        return workflowService.getWorkflow(id);
    }

    /** PUT /workflows/{id} — Replace workflow definition */
    @PutMapping("/{id}")
    public WorkflowResponse updateWorkflow(
            @PathVariable("id") UUID id,
            @RequestBody UpdateWorkflowRequest request) {
        return workflowService.updateWorkflow(id, request);
    }

    /** DELETE /workflows/{id} — Delete workflow */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkflow(@PathVariable("id") UUID id) {
        workflowService.deleteWorkflow(id);
    }

    /** DELETE /workflows/{id}/xcom — Clear XCom history */
    @DeleteMapping("/{id}/xcom")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearXComHistory(@PathVariable("id") UUID id) {
        workflowService.clearXComHistory(id);
    }

    // ── Lifecycle controls ───────────────────────────────────────────────────


    /** POST /workflows/{id}/execute — Start execution (DRAFT → RUNNING) */
    @PostMapping("/{id}/execute")
    public WorkflowResponse executeWorkflow(@PathVariable("id") UUID id) {
        return workflowService.executeWorkflow(id);
    }

    /** POST /workflows/{id}/pause — Pause execution (RUNNING → PAUSED) */
    @PostMapping("/{id}/pause")
    public WorkflowResponse pauseWorkflow(@PathVariable("id") UUID id) {
        return workflowService.pauseWorkflow(id);
    }

    /** POST /workflows/{id}/resume — Resume execution (PAUSED → RUNNING) */
    @PostMapping("/{id}/resume")
    public WorkflowResponse resumeWorkflow(@PathVariable("id") UUID id) {
        return workflowService.resumeWorkflow(id);
    }

    /** POST /workflows/{id}/reset — Reset to DRAFT */
    @PostMapping("/{id}/reset")
    public WorkflowResponse resetWorkflow(@PathVariable("id") UUID id) {
        return workflowService.resetWorkflow(id);
    }

    // ── DAG ──────────────────────────────────────────────────────────────────

    /** GET /workflows/{id}/dag — Full DAG (nodes + edges) */
    @GetMapping("/{id}/dag")
    public DagGraphResponse getDag(@PathVariable("id") UUID id) {
        return workflowService.getDag(id);
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    /** GET /workflows/{id}/jobs — Get all jobs for this workflow */
    @GetMapping("/{id}/jobs")
    public Page<JobResponse> getWorkflowJobs(
            @PathVariable("id") UUID id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return jobService.getJobsByWorkflow(id, page, size);
    }

    /** GET /workflows/{id}/jobs/{label} — Get a specific job by label */
    @GetMapping("/{id}/jobs/{label}")
    public JobResponse getWorkflowJobByLabel(
            @PathVariable("id") UUID id,
            @PathVariable("label") String label) {
        return jobService.getJobByWorkflowAndLabel(id, label);
    }
}
