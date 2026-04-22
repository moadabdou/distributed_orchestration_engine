# Fern-OS Python SDK Documentation

Welcome to the documentation for the Fern-OS Python SDK. This SDK provides everything you need to build, deploy, and manage workflows on the Fern-OS platform.

## Quick Links

- **[Getting Started](getting-started.md)**: Install the SDK and run your first workflow.
- **[Client Reference](client.md)**: Learn how to use the `FernOSClient` to interact with the Manager.
- **[Workflows & Dependencies](workflows.md)**: Define complex DAGs and manage execution order.
- **[Job Types](jobs.md)**: Explore the different types of jobs available (Python, Shell, Utility).
- **[XCom (Communication)](xcom.md)**: Share data between jobs in a workflow.
- **[Real-Time Events](events.md)**: Build reactive systems using the asynchronous event system.
- **[Storage](storage.md)**: Handle large data files using MinIO.

## Key Features

- **Intuitive DAG Definition**: Use Python context managers and operators (`>>`, `<=`) to define workflows.
- **Type-Safe Jobs**: Specialized job classes for different execution environments.
- **Data Dependencies**: Built-in support for signaling and data flow between tasks.
- **Integrated Storage**: Easy interaction with MinIO for large data processing.
- **Real-Time Monitoring**: Dedicated event system for low-latency communication.

## Example Workflows

Check out the [Examples](examples.md) page for more complex real-world scenarios, including:
- Multi-stage data pipelines.
- Conditional branching.
- Event-driven orchestration.
