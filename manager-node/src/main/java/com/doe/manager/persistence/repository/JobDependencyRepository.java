package com.doe.manager.persistence.repository;

import com.doe.manager.persistence.entity.JobDependencyEntity;
import com.doe.manager.persistence.entity.JobDependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobDependencyRepository extends JpaRepository<JobDependencyEntity, JobDependencyId> {
    List<JobDependencyEntity> findByDependentJobId(UUID dependentJobId);
    void deleteByDependentJobId(UUID dependentJobId);
}
