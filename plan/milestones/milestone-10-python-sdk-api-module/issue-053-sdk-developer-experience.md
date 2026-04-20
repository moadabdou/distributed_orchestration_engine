# Issue #053: Python SDK CLI & Developer Tooling

## Description

Enhance the developer experience by providing command-line tools for workflow management and local testing.

## Requirements

1.  Add a CLI entry point `fernos` using `click` or `argparse`.
2.  Command `fernos run <file.py>` to submit a workflow from a file.
3.  Command `fernos status <workflow_id>` to check progress.
4.  Command `fernos logs <job_id>` that uses the new raw text log endpoint.
5.  Add project templates for quick start.

## Technical Details

- Use `entry_points` in `setup.py` or `pyproject.toml`.
- Integrate with the `ApiClient` implemented in #051.

## Acceptance Criteria

- [ ] `fernos --help` shows available commands
- [ ] Users can trigger a workflow and stream logs to their terminal
- [ ] SDK is easy to install and use locally
