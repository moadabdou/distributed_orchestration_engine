# Issue #059 — Event System Integration & Verification

## Description
Perform end-to-end testing of the event system and provide usage examples.

## Requirements
- Create a "Producer" job that emits events.
- Create a "Consumer" job that listens for events.
- Verify that signaling works across different workers (if applicable) or multiple jobs on the same worker.
- Document the API in the README/examples.

## Acceptance Criteria
- Integration test passes with real message exchange.
- Latency is within acceptable limits for real-time signaling.
