package com.doe.manager.persistence.repository;

import com.doe.core.model.WorkflowStatus;
import com.doe.manager.persistence.entity.WorkflowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
    List<WorkflowEntity> findByStatus(WorkflowStatus status);
    Page<WorkflowEntity> findByStatus(WorkflowStatus status, Pageable pageable);
    List<WorkflowEntity> findAllByOrderByCreatedAtDesc();
}
