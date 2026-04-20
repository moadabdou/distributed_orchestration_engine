# Issue #050: Fluent Python Workflow Definition API

## Description

Provide a high-level Python API to define workflows (DAGs) without manually editing JSON payloads. This should feel familiar to users of Airflow or Prefect.

## Requirements

1.  Implement a `DAG` class for use as a context manager (`with DAG(...) as dag:`).
2.  Implement a `Job` class that accepts a `label` and a `path` to a Python script.
3.  Support defining dependencies between `Job` objects using the bitshift operator (`>>`).
4.  Implement a mechanism to bundle the DAG structure and referenced scripts for deployment.
5.  Maintain an internal graph of jobs based on the defined dependencies.

## Technical Details

- Location: `python-sdk/fernos_sdk/core.py`.
- The `path` argument should be relative to the DAG definition file.
- `Job` instantiation should register the job into the current `DAG` context.

## Acceptance Criteria

- [ ] DAGs can be defined using `Job(label="...", path="...")`
- [ ] Dependencies are correctly captured via the `>>` operator
- [ ] Multiple jobs can point to the same script with different labels/params
