# Issue #052: Integrated MinIO & XCom Utilities in Python SDK

## Description

Simplify the usage of shared storage (MinIO) and inter-job communication (XCom) for Python task developers.

## Requirements

1.  Implement an `XComClient` that supports the `get_xcom(job_id=...)` function.
2.  Implement automatic XCom push: task return values (specifically dictionaries) should be captured and pushed to the backend via stdout intercept or explicit manager call.
3.  Support serializing/deserializing Python objects (pickle or json) automatically for XComs.
4.  Provide a `MinioClient` wrapper for easy storage interaction within task functions.

## Technical Details

- Location: `python-sdk/fernos_sdk/context.py` and `python-sdk/fernos_sdk/storage.py`.
- Ensure compatibility with Java-side XCom implementation.

## Acceptance Criteria

- [ ] Task code can pass data simply by `return {"key": value}`
- [ ] Data is pulled from upstream using `get_xcom(job_id="upstream_job")`
- [ ] MinIO client is easily accessible within the task body
