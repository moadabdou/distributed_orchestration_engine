CREATE TABLE job_dependencies (
    dependent_job_id  UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    depends_on_id     UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    PRIMARY KEY (dependent_job_id, depends_on_id),
    CONSTRAINT no_self_dependency
        CHECK (dependent_job_id <> depends_on_id)
);

CREATE UNIQUE INDEX idx_dep_pair ON job_dependencies(dependent_job_id, depends_on_id);
