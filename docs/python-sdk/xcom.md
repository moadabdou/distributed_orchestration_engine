# XCom: Cross-Communication

XCom (Cross-Communication) allows jobs to share small amounts of data. A job can "push" a value to XCom, and subsequent jobs in the same workflow can "pull" that value.

## How it works

XComs are scoped to a specific workflow execution. When a job pushes data, it is stored in the Fern-OS Manager. Other jobs can then retrieve this data using the key it was pushed with.

## Usage in Python Scripts

The SDK provides a global `xcom` object that you can use within your `PythonJob` scripts.

### Pushing Data

```python
from fernos import xcom

# Push a simple value
xcom.push(key="model_accuracy", value=0.98)

# Push a complex object (must be JSON serializable)
xcom.push(key="metrics", value={
    "precision": 0.97,
    "recall": 0.99
})
```

### Pulling Data

```python
from fernos import xcom

# Pull data from an upstream job using its label or a specific key
accuracy = xcom.pull(key="model_accuracy")

if accuracy:
    print(f"Received accuracy: {accuracy}")
```

## Signaling and XCom

When using the signal operator (`<=` or `.signals()`), it often implies that the downstream job will pull data from the upstream job.

```python
# In your DAG definition
trainer >> evaluator
evaluator <= trainer  # Signal that trainer provides data to evaluator
```

```python
# In evaluator.py
trained_model_path = xcom.pull(key="trainer") # By default, the label is used as the key for the primary output
```

## Best Practices

- **Size Limits**: XCom is intended for small metadata, configuration, or paths. For large datasets, use [Storage](storage.md) (like MinIO) and pass the path via XCom.
- **Serialization**: Ensure values are JSON serializable (dicts, lists, strings, numbers, booleans).
- **Thread Safety**: The `xcom` object is thread-safe and can be used in multi-threaded scripts.
