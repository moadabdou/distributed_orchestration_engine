# Milestone 1: Core Engine & Protocol verification
## Manual Testing & Verification Plan

This document outlines the testing procedures to verify the core infrastructure established in Milestone 1, including the binary communication protocol, worker registration, the exponential backoff reconnection policy, and the resilient heartbeat mechanism.

These tests should be executed manually from a terminal to ensure the distributed components interact correctly in real-time.

---

### Prerequisites: Build the Project

Before beginning manual verification, compile all modules and build the executable JARs.

```bash
# From the project root directory
mvn clean package -pl engine-core,manager-node,worker-node -DskipTests
```

---

### Test Scenario 1: Basic Node Registration & Concurrency

**Objective:** Verify that the Manager Node can bind to a port, accept incoming TCP connections using Java 21 Virtual Threads, decode the `REGISTER_WORKER` message, and respond with a `REGISTER_ACK` containing a server-assigned UUID.

1. **Start the Manager Node (Terminal 1)**
   ```bash
   java -jar manager-node/target/manager-node-1.0.0-SNAPSHOT.jar --port 9090
   ```
   *Expected Output:* The manager should log that it has successfully bound to port 9090 and that the `HeartbeatMonitor` is active.

2. **Start Worker A (Terminal 2)**
   ```bash
   java -jar worker-node/target/worker-node-1.0.0-SNAPSHOT.jar --host localhost --port 9090
   ```
   *Expected Output (Worker A):* The worker will log that it connected successfully and sent the `REGISTER_WORKER` frame. It will then log `Registered with manager, assigned worker ID: <UUID-A>`.
   *Expected Output (Manager):* The manager will log `Worker <UUID-A> connected from 127.0.0.1:<PORT> (hostname: ...)`.

3. **Start Worker B (Terminal 3)**
   ```bash
   java -jar worker-node/target/worker-node-1.0.0-SNAPSHOT.jar --host localhost --port 9090
   ```
   *Expected Output:* The manager should seamlessly handle this concurrent connection, assigning a distinct `<UUID-B>` to Worker B without blocking the network thread.

---

### Test Scenario 2: Egress Queue & Heartbeats

**Objective:** Verify that the non-blocking egress queue operates correctly and that the workers send autonomous background heartbeats.

1. **Observe Terminals 2 & 3 (The Workers)**
   *Expected Output:* Exactly every 5 seconds, both workers should log `Worker <UUID>: sent HEARTBEAT`. This confirms that the dedicated virtual thread `egress-writer` is correctly polling the `LinkedBlockingQueue` and writing to the socket.

2. **Enable Manager Debug Logging (Optional)**
   If the manager's logger is configured to `DEBUG` level for `com.doe.manager.server.ManagerServer`, it will log `Heartbeat received from Worker <UUID-A>` and `<UUID-B>` every 5 seconds.

---

### Test Scenario 3: Graceful Disconnect & Exponential Backoff

**Objective:** Verify that workers gracefully handle manager termination and aggressively attempt to reconnect using the `RetryPolicy` without crashing.

1. **Kill the Manager Node**
   In Terminal 1 (Manager), press `Ctrl+C`.

2. **Observe the Workers**
   *Expected Output:* Both Worker A and Worker B will immediately detect the `EOF` or `SocketException` (connection reset). They will log the failure and enter the automatic retry loop:
   ```text
   Connection attempt 1/∞ failed: Connection refused. Retrying in 1 ms...
   Connection attempt 2/∞ failed: Connection refused. Retrying in 2 ms...
   ...
   ```
   They will cap at a 30-second sleep interval.

3. **Restart the Manager Node**
   In Terminal 1, restart the manager using the command from Scenario 1.

4. **Observe the Recovery**
   *Expected Output:* Both workers will successfully reconnect on their next exponential tick. They will send *new* `REGISTER_WORKER` frames, receive *new* UUIDs from the manager, and seamlessly resume their heartbeat loops.

---

### Test Scenario 4: Dead Worker Eviction (Issue #005)

**Objective:** Verify the Manager's `ScheduledExecutorService` reliably detects partitioned or abruptly killed workers by enforcing the `heartbeatTimeoutMs` policy.

1. **Establish a Healthy State**
   Ensure both the Manager (Terminal 1) and Worker A (Terminal 2) are running and actively exchanging heartbeats.

2. **Simulate a Hard Crash on Worker A**
   In Terminal 2, find the worker's Process ID (PID) and send a `SIGKILL` to simulate an ungraceful hardware or network failure where the TCP socket is not cleanly closed (`FIN`/`ACK`).
   
   *Alternative:* For testing convenience, if `Ctrl+C` sends `SIGINT` and triggers the shutdown hook cleanly, try suspending the process in Unix using `Ctrl+Z` (which freezes the process, halting heartbeats but leaving the TCP connection lingering).

3. **Observe the Manager (Terminal 1)**
   *Expected Output:* The worker will cease transmitting heartbeats. The manager will remain silent for ~15 seconds. Then, the `HeartbeatMonitor` periodic task will wake up, evaluate the stale `AtomicReference<Instant> lastHeartbeat`, and log:
   ```text
   WARN: Worker <UUID-A> marked DEAD (no heartbeat for 1500X ms)
   ```
   The manager will forcefully close the lingering socket to reclaim descriptors and unregister the stale worker from the `WorkerRegistry`.
