# Issue 038.5: Database Schema & JPA Entities for Workflows

## Phase
**Phase 2: Persistence — DB Schema + Recovery**

## Description
Create Flyway migrations for workflow persistence and define JPA entities and repositories for workflow and job dependency storage.

## Scope

### 1. Database Migrations

#### `V2__create_workflows.sql`
```sql
CREATE TABLE workflows (
    id          UUID        PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL
);
```

#### `V3__add_workflow_to_jobs.sql`
```sql
ALTER TABLE jobs ADD COLUMN workflow_id UUID REFERENCES workflows(id) ON DELETE SET NULL;
ALTER TABLE jobs ADD COLUMN dag_index  INTEGER;
```

#### `V4__create_job_dependencies.sql`
```sql
CREATE TABLE job_dependencies (
    dependent_job_id  UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    depends_on_id     UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    PRIMARY KEY (dependent_job_id, depends_on_id),
    CONSTRAINT no_self_dependency
        CHECK (dependent_job_id <> depends_on_id)
);

CREATE UNIQUE INDEX idx_dep_pair ON job_dependencies(dependent_job_id, depends_on_id);
```

#### `V5__auto_workflow_index.sql`
```sql
-- Add a default workflow for orphan jobs (jobs without workflow_id)
INSERT INTO workflows (id, name, status, created_at, updated_at)
SELECT gen_random_uuid(), 'auto-legacy-workflow', 'COMPLETED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM workflows WHERE name = 'auto-legacy-workflow');

UPDATE jobs SET workflow_id = (SELECT id FROM workflows WHERE name = 'auto-legacy-workflow')
WHERE workflow_id IS NULL;
```

### 2. JPA Entities

#### `WorkflowEntity`
```java
@Entity
@Table(name = "workflows")
public class WorkflowEntity {
    @Id
    private UUID id;
    private String name;
    @Enumerated(STRING)
    private WorkflowStatus status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

#### `JobEntity` (extended)
- Add `@ManyToOne` relationship to `WorkflowEntity` via `workflow_id`
- Add `dagIndex` column mapping

#### `JobDependencyEntity` + `JobDependencyId`
```java
@Entity
@Table(name = "job_dependencies")
public class JobDependencyEntity { ... }

@Embeddable
public class JobDependencyId implements Serializable { ... }
```

### 3. Repositories

```java
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
    List<WorkflowEntity> findByStatus(WorkflowStatus status);
    List<WorkflowEntity> findAllByOrderByCreatedAtDesc();
}

public interface JobDependencyRepository extends JpaRepository<JobDependencyEntity, JobDependencyId> {
    List<JobDependencyEntity> findByDependentJobId(UUID jobId);
    void deleteByDependentJobId(UUID jobId);
}
```

## Acceptance Criteria
- [x] All 4 Flyway migrations created and apply cleanly
- [x] JPA entities map correctly to database tables
- [x] Repositories support CRUD operations and status queries
- [x] Cascade deletes work: deleting a workflow deletes its jobs and dependencies
- [x] `WorkflowRepositoryTest` and `JobDependencyRepositoryTest` pass with Testcontainers
- [x] Foreign key constraints enforce referential integrity

## Deliverables
```
manager-node/
  src/main/resources/db/migration/
    V2__create_workflows.sql
    V3__add_workflow_to_jobs.sql
    V4__create_job_dependencies.sql
    V5__auto_workflow_index.sql

  src/main/java/com/doe/manager/persistence/entity/
    WorkflowEntity.java
    JobDependencyEntity.java
    JobDependencyId.java

  src/main/java/com/doe/manager/persistence/repository/
    WorkflowRepository.java
    JobDependencyRepository.java

  src/test/java/com/doe/manager/persistence/
    WorkflowRepositoryTest.java
    JobDependencyRepositoryTest.java
```

## Dependencies
- Issue 038.4 (Phase 1 Integration & Testing — Engine)
- Existing `JobEntity` from previous milestones
- Testcontainers for PostgreSQL integration tests

## Notes
- Ensure FK constraints use `ON DELETE CASCADE` for proper cleanup
- Recovery idempotency: use workflow `id` as the key (not auto-generated)
- Loading all workflows on startup could be slow at scale; consider filtering by status in the future
