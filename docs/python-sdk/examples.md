# Python SDK Examples

This page provides various workflow examples to help you understand how to use the Fern-OS Python SDK for different scenarios.

## 1. Simple Sequential Execution

A basic workflow where jobs run one after another.

```python
with DAG("sequential_demo") as dag:
    t1 = EchoJob("task_1", "Step 1")
    t2 = EchoJob("task_2", "Step 2")
    t3 = EchoJob("task_3", "Step 3")

    t1 >> t2 >> t3
```

## 2. Parallel Execution (Fan-out/Fan-in)

Runs multiple tasks in parallel and waits for all of them to complete.

```python
with DAG("parallel_demo") as dag:
    start = EchoJob("start", "Beginning")
    
    # Fan-out: start triggers t1, t2, and t3
    t1 = EchoJob("worker_1", "Work A")
    t2 = EchoJob("worker_2", "Work B")
    t3 = EchoJob("worker_3", "Work C")
    
    start >> [t1, t2, t3]
    
    # Fan-in: finish waits for all workers
    finish = EchoJob("finish", "End")
    [t1, t2, t3] >> finish
```

## 3. Data Pipeline (Event-Driven & Signaling)

A complex example showing data flow signaling between jobs.

```python
with DAG("data_pipeline") as dag:
    generator = PythonJob(
        label="generator",
        script_path="scripts/generator.py"
    )

    transformer = PythonJob(
        label="transformer",
        script_path="scripts/transformer.py"
    )

    loader = PythonJob(
        label="loader",
        script_path="scripts/loader.py"
    )

    # Use <= to indicate data dependency/signaling
    transformer <= generator
    loader <= transformer
```

## 4. XCom for Dynamic Data Sharing

How to pass values between Python jobs.

**Job A (Producer):**
```python
from fernos import xcom
xcom.push("result_id", "ABC-123")
```

**Job B (Consumer):**
```python
from fernos import xcom
id = xcom.pull("result_id")
print(f"Processing ID: {id}")
```

**Workflow Definition:**
```python
with DAG("xcom_demo") as dag:
    producer = PythonJob("producer", script_path="producer.py")
    consumer = PythonJob("consumer", script_path="consumer.py")
    
    producer >> consumer
```

## 5. Using Virtual Environments

isolate dependencies for your Python jobs.

```python
with DAG("venv_demo") as dag:
    task = PythonJob(
        label="ml_training",
        script_path="train.py",
        venv="/path/to/my/ml-venv"
    )
```

## 6. Data Processing with Storage

How to use `FernMiniIO` to share large datasets between jobs.

**Producer Job:**
```python
import io
import pandas as pd
from fernos import FernMiniIO, xcom

storage = FernMiniIO()
client = storage.get_client()
bucket = storage.get_bucket()

# Generate and upload data
df = pd.DataFrame({"val": [1, 2, 3]})
csv_data = df.to_csv().encode()
filename = "data.csv"

client.put_object(bucket, filename, io.BytesIO(csv_data), len(csv_data))

# Pass filename to next job
xcom.push("data_file", filename)
```

**Consumer Job:**
```python
from fernos import FernMiniIO, xcom

storage = FernMiniIO()
filename = xcom.pull("data_file")

if filename:
    response = storage.get_client().get_object(storage.get_bucket(), filename)
    print(f"Reading {filename}...")
    # Process data...
    response.close()
    response.release_conn()
```
