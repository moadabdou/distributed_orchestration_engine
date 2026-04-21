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
| :--- | :--- | :--- |
| Python API Style | Context Manager & Builder-based | Airflow/Prefect inspiration for familiar DX and clear dependency management |
| Log Format | `text/plain` | Simplifies CLI and programmatic consumption |
| MinIO Client | Boto3 / Minio-py wrapper | Standard Python client with simplified Fern-OS configuration |
| XCom Interface | `from fernos.worker import xcom` | Direct push/pull from within job scripts |

## Implementation Example

### 1. The Helper File (`utils/text_cleaner.py`)
```python
def clean_text(raw_text):
    """Simulates a heavy text cleaning operation."""
    return raw_text.strip().lower()
```

### 2. Job 1: The Downloader (`jobs/download_data.py`)
```python
from fernos.worker import xcom

print("Initiating data download...")
heavy_file_path = "/shared/data/frieren_lore.txt"
print(f"Data saved to {heavy_file_path}")

# Push status and metadata to the Control Plane
xcom.push({
    "status": "download_complete",
    "storage_path": heavy_file_path
})
```

### 3. Job 2: The Processor (`jobs/process_data.py`)
```python
from fernos.worker import xcom

# @fernos_include
import utils/text_cleaner.py

# Pull data from upstream (blocks until ready)
upstream_data = xcom.pull("download_frieren_lore")
file_path = upstream_data.get("storage_path")

print(f"Reading raw data from {file_path}...")
raw_data = "   AuRa, DrOp yOuR WeApoNs.   "

# Use injected helper function
cleaned_data = text_cleaner.clean_text(raw_data)
print(f"Processed output: {cleaned_data}")

xcom.push({
    "status": "processing_complete",
    "final_preview": cleaned_data
})
```

### 4. The DAG Constructor (`frieren_dag.py`)
```python
from fernos import DAG, Job

with DAG(name="frieren_data_pipeline") as dag:
    task1 = Job(label="download_frieren_lore", path="jobs/download_data.py")
    task2 = Job(label="clean_and_process", path="jobs/process_data.py")
    
    task1 >> task2
```

### 5. Deployment
```bash
fernos deploy frieren_dag.py --host http://manager-node:8080
```

## Success Criteria

- [ ] Users can define DAGs using `Job(path=...)` pointing to external scripts
- [ ] Scripts can import local utilities via `# @fernos_include` preprocessor
- [ ] XCom operations work within scripts via `fernos.worker.xcom`
- [ ] Dependencies are defined using the `>>` bitshift operator between `Job` objects
- [ ] `fernos deploy` command automates DAG registration and script upload
- [ ] `/api/v1/logs/jobs/{id}/raw` returns pure text logs
- [ ] Python SDK is installable as a package (`pip install .`)

## Dependencies

- Milestone 9 (Pluggable Executors)
- Java Manager API (Milestone 3)
- MinIO Infrastructure (Milestone 6)

## Estimated Effort

**5–7 working days**
