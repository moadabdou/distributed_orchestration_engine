-- Flyway migration V7: Add SKIPPED status and enforce valid status values
-- Issue: Implementing SKIPPED Job Status

-- We don't have a check constraint on jobs.status in V1-V6, but workers.status has one.
-- Adding one now ensures data integrity for the new SKIPPED state.

ALTER TABLE jobs ADD CONSTRAINT check_job_status 
CHECK (status IN ('PENDING', 'ASSIGNED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED'));
