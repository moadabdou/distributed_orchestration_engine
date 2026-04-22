package com.doe.manager.persistence.repository;

import com.doe.manager.persistence.entity.DataDependencyEntity;
import com.doe.manager.persistence.entity.DataDependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DataDependencyRepository extends JpaRepository<DataDependencyEntity, DataDependencyId> {

    void deleteBySourceJobId(UUID jobId);
    void deleteByTargetJobId(UUID jobId);
}
