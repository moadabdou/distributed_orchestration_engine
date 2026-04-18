# Issue 045: SQL/Database Operator

> [!IMPORTANT]
> **SUPERSEDED**: This specialized Java operator has been dropped in favor of using the `PythonTaskExecutor`.
> Database operations should now be performed using Python scripts with libraries like `sqlalchemy` and `psycopg2-binary`, which are pre-installed in the worker nodes.

## Description
Implement a SQL operator that executes queries against various databases (PostgreSQL, MySQL, BigQuery, etc.) with result handling.

## Requirements
- Support multiple database types via JDBC drivers (PostgreSQL, MySQL, SQLite, BigQuery, Snowflake)
- Configurable connection parameters (host, port, database, username, password)
- Support SELECT, INSERT, UPDATE, DELETE, and DDL statements
- Capture query results (row count, result set with size limits)
- Connection pooling for repeated queries to same database
- Secret management for database credentials (environment variables, vault integration)
- Query timeout configuration

## Acceptance Criteria
- [ ] Queries execute against PostgreSQL, MySQL, and SQLite
- [ ] Connection parameters are configurable per job
- [ ] Query results are captured and stored (with configurable row limit)
- [ ] Connection pooling improves performance for repeated queries
- [ ] Database credentials are securely managed (not hardcoded)
- [ ] Query timeouts are enforced correctly

## Dependencies
- Issue 042 Pluggable Executor SPI
