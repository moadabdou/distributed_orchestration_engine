-- Flyway migration V2: Add capacity columns and update status check

ALTER TABLE workers ADD COLUMN max_capacity INTEGER NOT NULL DEFAULT 4;
ALTER TABLE workers ADD COLUMN active_job_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE workers DROP CONSTRAINT workers_status_check;
ALTER TABLE workers ADD CONSTRAINT workers_status_check CHECK (status IN ('ONLINE', 'OFFLINE'));

UPDATE workers SET status = 'ONLINE' WHERE status IN ('IDLE', 'BUSY');
