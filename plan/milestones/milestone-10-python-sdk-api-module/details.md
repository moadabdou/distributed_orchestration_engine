# Milestone 10: Python SDK & API Module

## Overview

Stabilize and enhance the Python developer experience by providing a high-level SDK for workflow definition and execution. This milestone focuses on making the engine easily accessible from Python, integrating with MinIO and XComs, and improving observability through raw log access for Fern-OS.

## Goals

1.  **Fluent Python API** — Define DAGs and tasks using a Pythonic, decorator-based or builder-based API for Fern-OS.
2.  **Workflow Execution Client** — Programmatically trigger workflows and monitor their status from Python.
3.  **Raw Text Log Access** — New endpoint for streaming or fetching non-HTML logs for programmatic parsing and CLI usage.
4.  **Integrated Storage (MinIO) & XComs** — Easy-to-use Python wrappers for interacting with the platform's storage and inter-job communication.
5.  **Pluggable Executor Refinements** — Ensure the Fern-OS Python SDK works seamlessly with the pluggable executors from Milestone 9.

## Architecture Decisions

| Decision | Choice | Rationale |
| Python API Style | Decorator-based (`@task`, `@workflow`) | Airflow/Prefect inspiration for familiar DX |
| Log Format | `text/plain` | Simplifies CLI and programmatic consumption |
| MinIO Client | Boto3 / Minio-py wrapper | Standard Python client with simplified Fern-OS configuration |
| XCom Interface | Context-aware `from fernos.context import get_xcom` | Minimal boilerplate for data exchange |

## Implementation Example

```python
from fernos import DAG, fernos_task
from fernos.context import get_xcom

# 1. Define the DAG context
with DAG(workflow_id="frieren_micro_rag", schedule="@daily") as dag:

    # 2. Use decorators to wrap standard Python functions into Fern-OS Jobs
    @fernos_task(job_id="download_frieren_wiki")
    def fetch_data():
        print("Downloading raw text...")
        file_path = "/shared/data/frieren.txt"
        # Returning a dictionary automatically triggers an XCom PUSH via stdout
        return {"storage_path": file_path} 

    @fernos_task(job_id="generate_embeddings")
    def embed_data():
        # The SDK abstracts the stdin XCom PULL handshake
        upstream_data = get_xcom(job_id="download_frieren_wiki")
        text_path = upstream_data["storage_path"]
        
        print(f"Reading from {text_path} and generating embeddings...")
        return {"embeddings_path": "/shared/data/embeds.npy"}

    # 3. Instantiate the tasks
    task1 = fetch_data()
    task2 = embed_data()

    # 4. Define the execution order (The famous Airflow bitshift operator)
    task1 >> task2
```

## Success Criteria

- [ ] Users can define a DAG with 5+ nodes using the `with DAG(...)` context manager
- [ ] Tasks are wrapped using `@fernos_task(job_id=...)` decorator
- [ ] Dependencies are defined using the `>>` bitshift operator
- [ ] Return values from tasks are automatically pushed as XComs
- [ ] `get_xcom(job_id=...)` successfully pulls data from upstream tasks
- [ ] Workflow can be triggered via `sdk.client.execute(workflow_id)`
- [ ] `/api/v1/logs/jobs/{id}/raw` returns pure text logs
- [ ] Python SDK is installable as a package (`pip install .`)

## Dependencies

- Milestone 9 (Pluggable Executors)
- Java Manager API (Milestone 3)
- MinIO Infrastructure (Milestone 6)

## Estimated Effort

**5–7 working days**
