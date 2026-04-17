-- Add a default workflow for orphan jobs (jobs without workflow_id)
INSERT INTO workflows (id, name, status, created_at, updated_at)
SELECT gen_random_uuid(), 'auto-legacy-workflow', 'COMPLETED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM workflows WHERE name = 'auto-legacy-workflow');

UPDATE jobs SET workflow_id = (SELECT id FROM workflows WHERE name = 'auto-legacy-workflow')
WHERE workflow_id IS NULL;
