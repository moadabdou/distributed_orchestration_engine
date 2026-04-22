# Issue #055 — Core Protocol Extensions for Events

## Description
Extend the `MessageType` enum and the core binary protocol to support high-performance real-time signaling between jobs and the manager.

## Requirements
- Add the following types to `MessageType` in `engine-core`:
  - `REGISTER_JOB_EVENTS` (0x0B): Setup initial connection (includes JWT).
  - `EVENT_REGISTER` (0x0C): Claim ownership.
  - `EVENT_SUBSCRIBE` (0x0D): Subscribe.
  - `EVENT_PUBLISH` (0x0E): Emit an event.
  - `EVENT_NOTIFY` (0x0F): Push notification.
- Ensure `ProtocolDecoder` and `ProtocolEncoder` handle these types.

## Acceptance Criteria
- Messages are correctly encoded and decoded via the binary protocol.
