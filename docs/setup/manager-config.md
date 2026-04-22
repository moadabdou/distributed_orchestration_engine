# Manager & Worker Configuration

Fern-OS is built on Spring Boot, providing a robust configuration system. While environment variables are the primary way to configure the system in Docker, you can fine-tune behavior via Spring properties.

## Port Mappings

The Manager node exposes two critical ports:

- **8080 (HTTP)**: REST API and Dashboard backend.
- **9090 (TCP)**: Proprietary binary protocol for low-latency communication with Worker nodes and the Python SDK (Events).

## Internal Scheduler Config

The following properties can be adjusted in the Manager's `application.yml` (or via ENV variables like `DOE_WORKFLOW_SCHEDULER_INTERVAL_MS`):

| Property | Default | Description |
| :--- | :--- | :--- |
| `doe.workflow.scheduler-interval-ms` | `1000` | Polling interval for the DAG scheduler. |
| `doe.workflow.fail-fast` | `true` | If true, a single job failure will cancel the entire workflow. |
| `doe.workflow.max-concurrent-jobs` | `10` | Parallelism limit per workflow execution. |
| `doe.workflow.recovery-mode` | `PAUSED_ON_RESTART` | How to handle active workflows after a manager reboot (`PAUSED_ON_RESTART` or `RESUME_AUTO`). |

## Worker Capacity

Each Worker node has a capacity (slots). By default, a worker can run 4 concurrent jobs. This is controlled by:

- `manager.worker.default-max-capacity`: `4`

## Heartbeats & Timeouts

- `doe.worker.heartbeat-interval-ms`: `5000` (Worker -> Manager)
- `doe.worker.read-timeout-ms`: `1200000` (20 minutes for long-running scripts)

> [!TIP]
> For production environments, it is recommended to set `doe.workflow.recovery-mode` to `RESUME_AUTO` to ensure system resilience.
