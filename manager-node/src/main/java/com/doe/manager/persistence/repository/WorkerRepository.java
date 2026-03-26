package com.doe.manager.persistence.repository;

import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.WorkerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkerEntity}.
 */
@Repository
public interface WorkerRepository extends JpaRepository<WorkerEntity, UUID> {

    /**
     * Finds all workers with the given status.
     *
     * @param status the status to filter by
     * @return list of matching workers
     */
    List<WorkerEntity> findByStatus(WorkerStatus status);
}
