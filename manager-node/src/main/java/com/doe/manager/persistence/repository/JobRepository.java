package com.doe.manager.persistence.repository;

import com.doe.core.model.JobStatus;
import com.doe.manager.persistence.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
}
