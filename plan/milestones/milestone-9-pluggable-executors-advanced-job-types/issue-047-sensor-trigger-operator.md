# Issue 047: Sensor/Trigger Operator

> [!IMPORTANT]
> **SUPERSEDED**: This specialized Java operator has been dropped in favor of using the `PythonTaskExecutor`.
> Sensor logic (polling files, endpoints, etc.) should now be implemented as Python scripts that "poke" their targets, leveraging Python's rich integration ecosystem.

## Description
Implement a sensor operator that waits for external events (file arrival, API response, time-based triggers) before proceeding with downstream jobs.

## Requirements
- **File Sensor:** Wait for a file to appear in a local or remote path (S3, GCS, NFS)
- **HTTP Sensor:** Poll an HTTP endpoint until a condition is met (status code, response body)
- **Time-based Sensor:** Trigger at specific times or intervals (cron-like, but with one-shot semantics)
- **SQL Sensor:** Poll a database query until a condition is met (row count, specific value)
- Configurable polling interval and timeout
- Fail or skip if timeout is reached without trigger
- Support for sensor mode (poke vs reschedule): poke keeps job RUNNING, reschedule frees the worker and re-schedules later

## Acceptance Criteria
- [ ] File sensor detects file arrival and triggers downstream jobs
- [ ] HTTP sensor polls endpoint until condition is met
- [ ] Time-based sensor triggers at configured times
- [ ] SQL sensor detects database condition changes
- [ ] Polling interval and timeout are configurable
- [ ] Timeout results in job failure or skip (configurable)
- [ ] Poke and reschedule modes work correctly

## Dependencies
- Issue 042 Pluggable Executor SPI
