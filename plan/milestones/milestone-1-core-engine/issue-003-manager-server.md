# Issue #003 — Build the Manager Server with Virtual Threads

**Milestone:** 1 — Core Engine  
**Labels:** `manager-node`, `networking`, `concurrency`, `priority:high`  
**Assignee:** —  
**Estimate:** 1.5 days  
**Depends on:** #002  

## Description

Implement the central `ManagerServer` that listens on a configurable TCP port, accepts incoming worker connections, and spawns a dedicated Virtual Thread per connection.

### Key Components

```
ManagerServer
├── ServerSocket (bind port)
├── WorkerRegistry (ConcurrentHashMap<UUID, WorkerConnection>)
└── accept loop
    └── Thread.ofVirtual().start(() -> handleWorker(socket))
```

## Acceptance Criteria
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder main class for the engine-core module.
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOG.info("Module: engine-core");
        System.out.println("engine-core module loaded.");
    }

- [ ] `ManagerServer` binds to a configurable port (default `9090`)
- [ ] Each accepted connection spawns a Virtual Thread (not a platform thread)
- [ ] `WorkerRegistry` stores `WorkerConnection(UUID id, Socket socket, Instant lastHeartbeat)`
- [ ] On `REGISTER_WORKER` message → add to registry, log `Worker <UUID> connected from <IP>`
- [ ] On `HEARTBEAT` message → update `lastHeartbeat` timestamp in registry
- [ ] Graceful shutdown: close `ServerSocket` and all active connections on SIGINT
- [ ] Integration test: connect 3 raw sockets, send registration messages, verify registry size = 3

## Technical Notes

- Use `try-with-resources` for socket lifecycle
- Configure `ServerSocket` with `SO_REUSEADDR = true` for faster restart during development
- Log with SLF4J + Logback; include worker UUID in every log line via MDC
