package com.doe.manager.persistence.repository;

import com.doe.manager.persistence.entity.XComEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface XComRepository extends JpaRepository<XComEntity, UUID> {
    
    /**
     * Finds the latest XCom value for a given key within a specific workflow run.
     * Note: In a DAG, we usually want the most recent push if multiple jobs push the same key,
     * although typically keys should be unique or per-job.
     */
    Optional<XComEntity> findFirstByWorkflowIdAndKeyOrderByCreatedAtDesc(UUID workflowId, String key);

    /**
     * Finds all XComs for a specific workflow ID.
     */
    java.util.List<XComEntity> findByWorkflowId(UUID workflowId);

    /**
     * Deletes all XComs for a specific workflow ID.
     */
    void deleteByWorkflowId(UUID workflowId);
}

