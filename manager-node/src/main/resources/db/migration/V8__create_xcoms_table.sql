-- Flyway migration V8: Create xcoms table
-- Issue #048 — XCom Support

CREATE TABLE xcoms (
    id             UUID         PRIMARY KEY,
    workflow_id    UUID         NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    job_id         UUID         NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    xcom_key       VARCHAR(255) NOT NULL,
    xcom_value     TEXT         NOT NULL,
    xcom_type      VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_xcoms_workflow_key ON xcoms(workflow_id, xcom_key);
CREATE INDEX idx_xcoms_job_id ON xcoms(job_id);
