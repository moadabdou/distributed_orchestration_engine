# Issue #058 — Python SDK Events Client Implementation

## Description
Implement the `Events` class in the Python SDK with support for event registration and workflow-scoped messaging.

## Requirements
- **`register(event_name)`**: Claim ownership of an event name.
- **`on(event_name, callback)`**: Subscribe to an event.
- **`emit(event_name, data)`**: Publish an event (should only be called after `register`).
- Manage background connection and reconnection logic.
- Ensure all calls use the `workflowId` from context.

## Acceptance Criteria
- SDK correctly implements the Register -> Emit flow.
- Multiple jobs can interact via events within a single workflow.
