# Issue 044: HTTP/API Operator

> [!IMPORTANT]
> **SUPERSEDED**: This specialized Java operator has been dropped in favor of using the `PythonTaskExecutor`. 
> Common HTTP tasks should now be performed using Python scripts with the `requests` library, which is pre-installed in the worker nodes.

## Description
Implement an HTTP operator that makes HTTP requests, handles retries, validates responses, and supports chaining API calls.

## Requirements
- Support GET, POST, PUT, DELETE, PATCH methods
- Configurable request headers, query parameters, and request body (JSON, form-data, raw)
- Response validation via status codes, JSON schema, or custom predicates
- Retry policies with exponential backoff for transient failures
- Timeout configuration per request
- Response body capture and storage as job result (with size limits)
- Support for authentication (Bearer tokens, Basic Auth, API keys)

## Acceptance Criteria
- [ ] HTTP requests execute with all supported methods
- [ ] Headers, query parameters, and request bodies are configurable
- [ ] Response validation rejects invalid responses and triggers retries
- [ ] Exponential backoff retry policy works correctly
- [ ] Response body is captured and stored (up to configurable size limit)
- [ ] Authentication methods work correctly

## Dependencies
- Issue 042 Pluggable Executor SPI
