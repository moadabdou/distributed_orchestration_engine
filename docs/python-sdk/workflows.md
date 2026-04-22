# Workflows and Dependencies

In Fern-OS, a workflow is defined as a Directed Acyclic Graph (DAG) of jobs.

## The `DAG` Context Manager

You define a workflow by wrapping job definitions within a `DAG` context manager.

```python
from fernos import DAG

with DAG(name="data_sync", description="Syncs data between systems") as dag:
    # Your jobs go here
    ...
```

### `DAG` Parameters

- `name`: A unique, human-readable name for the workflow.
- `description`: An optional description.

## Defining Dependencies

Dependencies determine the order in which jobs are executed. Fern-OS supports multiple operators for defining these relationships.

### The Right Shift Operator (`>>`)

Used to say that job B depends on job A (A must finish before B starts).

```python
task_a >> task_b
```

You can also define dependencies for multiple jobs at once:

```python
task_a >> [task_b, task_c]  # Both B and C wait for A
[task_a, task_b] >> task_c  # C waits for both A and B
```

### The Left Shift Operator (`<<`)

The inverse of `>>`. It means the left side depends on the right side.

```python
task_b << task_a  # task_b depends on task_a
```

### Data Dependencies / Signals (`<=` and `.signals()`)

Data dependencies (also called signals) are used when a job needs data from another job. While they still imply execution order, they can be visualized differently in the UI.

```python
# task_b needs data produced by task_a
task_b <= task_a

# Equivalent to
task_a.signals(task_b)
```

## How it works

When you define a dependency:
1. The `to` job is added to the `upstream` list of the `from` job (or `data_upstream` if using `<=`).
2. The `DAG` object tracks all these relationships.
3. When you call `client.register_dag(dag)`, the entire graph is serialized and sent to the Manager.
