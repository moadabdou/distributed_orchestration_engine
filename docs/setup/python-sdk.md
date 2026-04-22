# Python SDK Setup

The Fern-OS Python SDK allows you to define, deploy, and monitor workflows programmatically.

## Installation

### From Source

Currently, the SDK is installed by navigating to the `python-sdk` directory:

```bash
cd python-sdk
pip install -e .
```

This installs the `fernos` package in editable mode, including its core dependencies:
- `requests`
- `minio`

## Configuration

The SDK automatically looks for the following environment variables to connect to the Manager:

| Variable | Default | Description |
| :--- | :--- | :--- |
| `FERNOS_MANAGER_HOST` | `localhost` | Host of the Manager node. |
| `FERNOS_MANAGER_HTTP_PORT` | `8080` | HTTP port for API calls. |
| `FERNOS_MANAGER_TCP_PORT` | `9090` | TCP port for real-time events. |

## Verification

You can verify your installation by running one of the examples:

```bash
cd python-sdk/examples
python complex_sleep_workflow.py
```

If the SDK is correctly configured, you should see logs indicating the workflow status transitions from `RUNNING` to `COMPLETED`.
