package com.doe.manager.persistence.repository;

import com.doe.core.model.JobStatus;
import com.doe.manager.persistence.entity.JobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link JobEntity}.
 *
 * <p>The {@link #findByStatus(JobStatus)} method uses the indexed {@code status}
 * column for efficient filtering, matching the index declared in {@link JobEntity}.
 */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    /**
     * Finds all jobs with the given status.
     * Uses the {@code idx_jobs_status} index for efficient querying.
     *
     * @param status the status to filter by
     * @return list of matching jobs ordered by default
     */
    List<JobEntity> findByStatus(JobStatus status);

    /**
     * Finds all jobs with the given status, with pagination support.
     *
     * @param status the status to filter by
     * @param pageable pagination information
     * @return a page of matching jobs
     */
    Page<JobEntity> findByStatus(JobStatus status, Pageable pageable);

    /**
     * Finds all jobs whose status is in the given collection.
     * Used by {@link com.doe.manager.recovery.StartupRecoveryService} on startup
     * to locate orphaned ASSIGNED/RUNNING jobs in a single query.
     *
     * @param statuses the set of statuses to match
     * @return list of matching jobs
     */
    List<JobEntity> findByStatusIn(Collection<JobStatus> statuses);

    /**
     * Finds all standalone jobs (not part of any workflow) with the given status.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.status = :status AND j.workflow IS NULL")
    List<JobEntity> findByStatusAndWorkflowIsNull(@Param("status") JobStatus status);

    /**
     * Finds all standalone jobs (not part of any workflow) whose status is in the given collection.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.status IN :statuses AND j.workflow IS NULL")
    List<JobEntity> findByStatusInAndWorkflowIsNull(@Param("statuses") Collection<JobStatus> statuses);

    /**
     * Finds all jobs associated with a given workflow ID.
     *
     * @param workflowId the ID of the workflow
     * @return list of matching jobs
     */
    List<JobEntity> findByWorkflowId(UUID workflowId);

    /**
     * Finds all jobs associated with a given workflow ID, with pagination support.
     *
     * @param workflowId the ID of the workflow
     * @param pageable pagination information
     * @return a page of matching jobs
     */
    Page<JobEntity> findByWorkflowId(UUID workflowId, Pageable pageable);

    /**
     * Finds a specific job by workflow ID and its label.
     *
     * @param workflowId the ID of the workflow
     * @param jobLabel the label of the job
     * @return an optional containing the job if found
     */
    Optional<JobEntity> findByWorkflowIdAndJobLabel(UUID workflowId, String jobLabel);

    /**
     * Counts all jobs associated with a given workflow ID.
     */
    int countByWorkflowId(UUID workflowId);
}
