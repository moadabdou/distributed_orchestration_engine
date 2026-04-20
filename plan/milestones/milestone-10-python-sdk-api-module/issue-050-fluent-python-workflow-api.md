# Issue #050: Fluent Python Workflow Definition API

## Description

Provide a high-level Python API to define workflows (DAGs) without manually editing JSON payloads. This should feel familiar to users of Airflow or Prefect.

## Requirements

1.  Implement a `DAG` class for use as a context manager (`with DAG(...) as dag:`).
2.  Implement a `@fernos_task` decorator that converts a Python function into a Fern-OS Job definition.
3.  Support defining dependencies between tasks using the bitshift operator (`>>`).
4.  Implement a task instantiation mechanism that registers the task within the active `DAG` context.
5.  Support task parameters (labels, timeouts, retries) within the decorator.

## Technical Details

- Location: `python-sdk/fernos_sdk/core.py`.
- Internal representation should map to the JSON schema expected by the Manager API.
- Use thread-local or contextvars to track the active `DAG` context.

## Acceptance Criteria

- [ ] DAGs can be defined using the `with DAG(...)` syntax
- [ ] Dependencies are correctly captured via the `>>` operator
- [ ] Support for parallel task execution via the API
