# FernOSClient Reference

The `FernOSClient` is the primary entry point for interacting with the Fern-OS Manager from Python.

## Initialization

```python
from fernos import FernOSClient

# Initialize with default settings (uses environment variables)
client = FernOSClient()

# Or specify a base URL explicitly
client = FernOSClient(base_url="http://my-manager:8080")
```

### Configuration via Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `FERNOS_MANAGER_HOST` | Hostname of the Manager | `localhost` |
| `FERNOS_MANAGER_HTTP_PORT` | HTTP port of the Manager | `8080` |

## Core Methods

### `register_dag(dag: DAG, execute: bool = True) -> RemoteWorkflow`

Registers a workflow (DAG) with the Manager.

- `dag`: The `DAG` object to register.
- `execute`: If `True`, the workflow starts executing immediately after registration.
- **Returns**: A `RemoteWorkflow` object.

### `get_workflow(workflow_id: UUID) -> RemoteWorkflow`

Retrieves a workflow by its ID.

### `list_workflows(page: int = 0, size: int = 20, status: Optional[str] = None) -> List[RemoteWorkflow]`

Lists workflows with optional filtering.

### `submit_job(job: Job) -> RemoteJob`

Submits an ad-hoc job directly (not as part of a DAG). Note: This is primarily for one-off tasks.

### `list_workers() -> List[Dict[str, Any]]`

Returns a list of all workers currently registered with the Manager.

## RemoteWorkflow

The `RemoteWorkflow` object returned by `register_dag` allows you to manage and monitor a workflow.

- `id`: The unique ID of the workflow.
- `name`: The name of the workflow.
- `status`: Current status (`DRAFT`, `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `PAUSED`).
- `execute()`: Starts/resumes execution.
- `pause()`: Pauses the workflow.
- `resume()`: Resumes a paused workflow.
- `reset()`: Resets the workflow to `DRAFT` (clears previous run state).
- `delete()`: Deletes the workflow.
- `wait_for_completion(timeout_sec: int = 3600)`: Blocks until the workflow is in a terminal state.

## RemoteJob

Represents a specific job within a workflow.

- `id`: Unique job ID.
- `label`: Human-readable label.
- `status`: Current status of the job.
- `cancel()`: Cancels a running job.
- `retry()`: Retries a failed job.
- `get_logs(raw: bool = True)`: Retrieves the logs for this job.
