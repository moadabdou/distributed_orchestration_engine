# Issue #057 — Job Token Generation & Authentication

## Description
Secure the direct SDK-to-Manager connection by using job-specific JWT tokens that encapsulate both workflow and job identity.

## Requirements
- Update `JobScheduler` in the Manager to generate a JWT token when a job is assigned.
- **JWT Subject**: Must be formatted as `workflowId:jobId`.
- Include this token in the `ASSIGN_JOB` payload sent to the worker.
- **Worker Injection**: Update `PythonTaskExecutor` (and others) to extract the token and inject it into the subprocess as `FERNOS_JOB_TOKEN`.
- **Manager Validation**: `ManagerServer` must validate the JWT during `REGISTER_JOB_EVENTS` and associate the connection with the extracted `workflowId` and `jobId`.

## Acceptance Criteria
- Jobs have access to `FERNOS_JOB_TOKEN`.
- Manager correctly extracts and associates session IDs from the token.
