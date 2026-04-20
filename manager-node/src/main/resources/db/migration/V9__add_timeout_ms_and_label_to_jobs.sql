-- V9: Add timeout_ms and job_label to jobs table
ALTER TABLE jobs ADD COLUMN timeout_ms BIGINT;
ALTER TABLE jobs ADD COLUMN job_label VARCHAR(255);

-- Set a default for existing jobs (e.g., 10 minutes = 600000ms)
UPDATE jobs SET timeout_ms = 600000 WHERE timeout_ms IS NULL;

-- Make timeout_ms NOT NULL after setting defaults
ALTER TABLE jobs ALTER COLUMN timeout_ms SET NOT NULL;
