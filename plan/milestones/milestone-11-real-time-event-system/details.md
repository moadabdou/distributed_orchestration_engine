# Milestone 11 — Real-time Event System

## Goal
Implement a real-time signaling system that allows jobs to communicate without constant XCom polling. This enables "continuous" workflows where producers can immediately notify consumers when data is ready.

## Objectives
- Extend the core binary protocol to support event messages.
- Implement specialized "Event" connection handling in the Manager.
- Develop a job-level authentication mechanism (Job Tokens) for direct SDK-to-Manager communication.
- Create a user-friendly `Events` class in the Python SDK for subscribing and publishing.

## Key Issues
- **[#055] Core Protocol Extensions**: Add `REGISTER_JOB_EVENTS`, `EVENT_SUBSCRIBE`, `EVENT_PUBLISH`, and `EVENT_NOTIFY` to the protocol.
- **[#056] Manager Event Routing**: Implement subscription management and message dispatching in `ManagerServer`.
- **[#057] Job Token Auth**: Generate JWT tokens for jobs and validate them during event connection registration.
- **[#058] Python SDK Events**: Develop the TCP client and callback system in the SDK.
- **[#059] Verification**: End-to-end testing with producer/consumer scripts.
- **[#060] Data Flow Edges**: Enhance the UI to visualize data/signal flow between concurrent jobs.


## Success Criteria
- Jobs can register callbacks for specific event names.
- Events published by one job are received by all subscribers in < 50ms (latency).
- No changes required to the Worker Node's JVM-side bridging logic (direct SDK communication).
