# Job Types Reference

Fern-OS supports several job types for different tasks. All jobs share a set of common parameters.

## Common Parameters

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `label` | `str` | A unique identifier for the job within the DAG. | **Required** |
| `timeout_ms` | `int` | Maximum execution time in milliseconds. | `300000` (5 mins) |
| `retry_count` | `int` | Number of times to retry if the job fails. | `0` |

---

## PythonJob

Executes a Python script. It handles dependency processing and environment management.

```python
from fernos import PythonJob

job = PythonJob(
    label="process_csv",
    script_path="scripts/processor.py",
    args=["--format", "csv"],
    env={"ENVIRONMENT": "production"},
    venv="my-venvs/data-science"
)
```

### Parameters

- `script`: Raw Python code as a string.
- `script_path`: Path to a `.py` file.
- `args`: List of command-line arguments.
- `env`: Dictionary of environment variables.
- `venv`: Path to a virtual environment to use.
- `conda_env`: Name/path of a Conda environment to use.

---

## ShellJob

Executes a bash script.

```python
from fernos import ShellJob

job = ShellJob(
    label="cleanup",
    script="rm -rf /tmp/staging/*"
)
```

### Parameters

- `script`: Raw shell script content.
- `script_path`: Path to a `.sh` file.

---

## Utility Jobs

### SleepJob
Pauses execution for a specified duration.

```python
from fernos import SleepJob

# Sleep for 10 seconds
job = SleepJob(label="wait", ms=10000)
```

### EchoJob
Returns the provided data. Useful for testing and simple signaling.

```python
from fernos import EchoJob

job = EchoJob(label="say_hi", data="Hello World")
```

### FibonacciJob
Calculates the Fibonacci sequence up to `n`. Primarily used for load testing and demonstrations.

```python
from fernos import FibonacciJob

job = FibonacciJob(label="compute_fib", n=10)
```
