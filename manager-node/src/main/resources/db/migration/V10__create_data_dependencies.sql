CREATE TABLE data_dependencies (
    source_job_id  UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    target_job_id  UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    PRIMARY KEY (source_job_id, target_job_id),
    CONSTRAINT no_self_data_dependency
        CHECK (source_job_id <> target_job_id)
);

CREATE UNIQUE INDEX idx_data_dep_pair ON data_dependencies(source_job_id, target_job_id);
