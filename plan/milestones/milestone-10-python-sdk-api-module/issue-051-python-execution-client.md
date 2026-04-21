# Issue #051: Python SDK Workflow Execution Client

## Description

Enable users to programmatically trigger and monitor Fern-OS workflows from Python scripts.

## Requirements

1.  Implement an `ApiClient` that communicates with the Manager's REST API.
2.  Method `submit_workflow(workflow_definition)` to register and start a workflow.
3.  Method `get_status(workflow_id)` to poll for completion.
4.  Method `wait_for_completion(workflow_id)` with timeout and polling interval.

## Technical Details

- Use `requests` or `httpx` for communication.
- Handle authentication (if implemented in M3/M8).

## Acceptance Criteria

- [x] Code snippet can submit a workflow and wait for it to finish
- [x] Handles network timeouts and API errors gracefully
