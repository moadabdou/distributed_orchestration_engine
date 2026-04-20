package com.doe.manager.workflow;

import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.entity.XComEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import com.doe.manager.persistence.repository.XComRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing cross-job communication (XComs).
 * Implements a cache to avoid frequent DB lookups within the same workflow.
 */
@Service
public class XComService {

    private static final Logger LOG = LoggerFactory.getLogger(XComService.class);

    private final XComRepository xComRepository;
    private final WorkflowRepository workflowRepository;
    private final JobRepository jobRepository;
    private final MinioService minioService;

    /**
     * Cache structure: workflowId -> (xcomKey -> xcomValue)
     * For simplicity, this cache lives in-memory. In a distributed multi-manager setup, 
     * a distributed cache like Redis would be preferred.
     */
    private final Map<UUID, Map<String, String>> xcomCache = new ConcurrentHashMap<>();

    public XComService(XComRepository xComRepository, 
                      WorkflowRepository workflowRepository, 
                      JobRepository jobRepository,
                      MinioService minioService) {
        this.xComRepository = xComRepository;
        this.workflowRepository = workflowRepository;
        this.jobRepository = jobRepository;
        this.minioService = minioService;
    }

    /**
     * Deletes all XComs for a given workflow, including following and deleting MinIO references.
     */
    @Transactional
    public void deleteXComsByWorkflowId(UUID workflowId) {
        LOG.info("Cleaning XCom history for workflow: {}", workflowId);

        // 1. Find all XComs to check for MinIO references
        java.util.List<XComEntity> entities = xComRepository.findByWorkflowId(workflowId);

        for (XComEntity entity : entities) {
            String type = entity.getType();
            String value = entity.getValue();

            // Check if it's a MinIO reference
            // Heuristic: type is "minio" or value starts with "minio://" or key ends with "_path"/"_data"
            // For now, let's be safe and check type="minio" or value starts with "minio://"
            // Given the user request, we should be proactive.
            if ("minio".equalsIgnoreCase(type) || (value != null && value.startsWith("minio://"))) {
                String path = value;
                if (value.startsWith("minio://")) {
                    path = value.substring(8); // strip minio://
                    if (path.contains("/")) {
                        path = path.substring(path.indexOf("/") + 1); // strip bucket name
                    }
                }
                minioService.deleteObject(path);
            }
        }

        // 2. Clear cache
        clearCache(workflowId);

        // 3. Delete from DB
        xComRepository.deleteByWorkflowId(workflowId);
        LOG.info("XCom history cleaned for workflow: {}", workflowId);
    }


    @Transactional
    public void push(UUID workflowId, UUID jobId, String key, String value, String type) {
        LOG.info("Pushing XCom: workflow={}, job={}, key={}, type={}", workflowId, jobId, key, type);

        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        XComEntity entity = new XComEntity(
                UUID.randomUUID(),
                workflow,
                job,
                key,
                value,
                type,
                Instant.now()
        );

        xComRepository.save(entity);

        // Update cache
        xcomCache.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Optional<String> pull(UUID workflowId, String key) {
        // Try cache first
        Map<String, String> workflowCache = xcomCache.get(workflowId);
        if (workflowCache != null && workflowCache.containsKey(key)) {
            LOG.debug("XCom cache hit: workflow={}, key={}", workflowId, key);
            return Optional.of(workflowCache.get(key));
        }

        // Try DB
        LOG.debug("XCom cache miss: workflow={}, key={}", workflowId, key);
        return xComRepository.findFirstByWorkflowIdAndKeyOrderByCreatedAtDesc(workflowId, key)
                .map(entity -> {
                    // Update cache for next time
                    xcomCache.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>()).put(key, entity.getValue());
                    return entity.getValue();
                });
    }

    /**
     * Clears cache for a specific workflow. Should be called when a workflow run finishes.
     */
    public void clearCache(UUID workflowId) {
        xcomCache.remove(workflowId);
    }
}
