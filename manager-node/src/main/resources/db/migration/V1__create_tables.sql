-- Flyway migration V1: Create workers and jobs tables
-- Issue #012 — JPA Entities, Repositories & Database Schema

CREATE TABLE workers (
    id             UUID         PRIMARY KEY,
    hostname       VARCHAR(255) NOT NULL,
    ip_address     VARCHAR(45)  NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('IDLE', 'BUSY', 'OFFLINE')),
    last_heartbeat TIMESTAMP    NOT NULL,
    registered_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE jobs (
    id          UUID        PRIMARY KEY,
    worker_id   UUID        REFERENCES workers(id),
    status      VARCHAR(20) NOT NULL,
    payload     JSONB       NOT NULL,
    result      TEXT,
    retry_count INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_jobs_status ON jobs(status);
