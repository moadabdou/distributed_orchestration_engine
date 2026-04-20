# Issue #049: Pure Text Log API Endpoint

## Description

The current `JobLogController` returns logs wrapped in an HTML template for browser viewing. While great for humans, this makes programmatic log consumption (CLI, SDK, log aggregators) difficult.

## Requirements

1.  Add a new GET endpoint `GET /api/v1/logs/jobs/{id}/raw`.
2.  The endpoint should return `text/plain` content.
3.  The response should be the pure, unwrapped log lines joined by newlines.
4.  Ensure proper error handling if logs are missing or if the job ID is invalid.

## Technical Details

- Modify `JobLogController.java`.
- Use `MediaType.TEXT_PLAIN_VALUE`.
- Leverage existing `jobService.getJobLogs(id)` but skip the HTML template generation.

## Acceptance Criteria

- [ ] `curl localhost:8080/api/v1/logs/jobs/{id}/raw` returns plain text logs
- [ ] Content-Type header is `text/plain`
- [ ] No HTML tags or scripts are present in the output
