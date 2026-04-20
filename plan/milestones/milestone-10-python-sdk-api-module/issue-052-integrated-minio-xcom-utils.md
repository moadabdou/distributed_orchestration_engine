# Issue #052: Integrated MinIO & XCom Utilities in Python SDK

## Description

Simplify the usage of shared storage (MinIO) and inter-job communication (XCom) for Python task developers.

## Requirements

1.  Create a dedicated `fernos.worker.xcom` module for use within job scripts.
2.  Implement `xcom.push(data)`: serializes the data (JSON) and writes it to a designated stdout channel or pipe for the Java worker to capture.
3.  Implement `xcom.pull(upstream_label)`: blocks on stdin until the Java worker provides the XCom data from the specified upstream job.
4.  Abstract the complex handshake (stdin/stdout) away from the user.

## Technical Details

- Location: `python-sdk/fernos_sdk/worker/xcom.py`.
- Use JSON as the primary serialization format for cross-language compatibility.
- The `pull` operation should support timeouts and error handling if the channel is closed.

## Acceptance Criteria

- [ ] Script code can pass data using `xcom.push({...})`
- [ ] Script code can pull data from upstream using `xcom.pull("label")`
- [ ] Handshake is robust and works across different OS environments (Linux/Docker)
