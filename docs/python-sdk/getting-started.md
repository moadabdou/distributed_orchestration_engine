# Getting Started with Fern-OS Python SDK

The Fern-OS Python SDK allows you to define, register, and manage complex workflows using Python. It provides a high-level API for orchestrating jobs, managing data dependencies, and interacting with the Fern-OS Manager.

## Prerequisites & Setup

Before you begin, ensure you have the Python SDK installed and configured. Refer to the [Python SDK Setup Guide](../setup/python-sdk.md) for detailed instructions on installation and environment configuration.

## Your First Workflow

Here is a simple "Hello World" workflow that runs a sequence of jobs.

```python
from fernos import DAG, PythonJob, EchoJob, FernOSClient

# 1. Initialize the client
client = FernOSClient()

# 2. Define the workflow using the DAG context manager
with DAG(name="my_first_workflow", description="A simple Hello World workflow") as dag:
    
    # 3. Create jobs
    hello = EchoJob(label="say_hello", data="Hello from Fern-OS!")
    
    task_1 = PythonJob(
        label="process_data",
        script="print('Processing data...')",
    )
    
    # 4. Define dependencies using the >> operator
    hello >> task_1

# 5. Register and execute the workflow
workflow = client.register_dag(dag)

print(f"Workflow registered with ID: {workflow.id}")
print(f"Current status: {workflow.status}")

# 6. Wait for completion
workflow.wait_for_completion()
print("Workflow completed successfully!")
```

## Next Steps

- Learn more about [Jobs](jobs.md) and their types.
- Understand [Workflows and Dependencies](workflows.md).
- Explore [XCom](xcom.md) for data sharing between jobs.
- Use the [Events](events.md) system for real-time notifications.
