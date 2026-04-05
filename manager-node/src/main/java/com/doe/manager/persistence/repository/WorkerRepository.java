package com.doe.manager.persistence.repository;

import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.WorkerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * Updates only the {@code lastHeartbeat} timestamp for a single worker.
     * <p>
     * Used by the buffered heartbeat flusher in {@code DatabaseEventListener} to
     * avoid loading the full entity on every heartbeat write.
     *
     * @param id the worker UUID
     * @param ts the new heartbeat timestamp
     * @return number of rows updated (0 if the worker was not found)
     */
    @Modifying
    @Query("UPDATE WorkerEntity w SET w.lastHeartbeat = :ts WHERE w.id = :id")
    int updateHeartbeat(@Param("id") UUID id, @Param("ts") Instant ts);

    /**
     * Sets the status of every worker row to the given value.
     * <p>
     * Called once on startup by {@link com.doe.manager.recovery.StartupRecoveryService}
     * to mark all previously-connected workers as OFFLINE (they lost their connection
     * when the Manager crashed/restarted).
     *
     * @param status the status to assign to all workers (typically {@code OFFLINE})
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE WorkerEntity w SET w.status = :status")
    int updateAllStatuses(@Param("status") WorkerStatus status);
}
