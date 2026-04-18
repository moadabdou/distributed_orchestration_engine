# Issue 046: Docker/Kubernetes Operator

> [!IMPORTANT]
> **SUPERSEDED**: This specialized Java operator has been dropped in favor of using the `PythonTaskExecutor`.
> Container and K8s orchestration should now be performed using Python scripts with the `docker` and `kubernetes` libraries, which are pre-installed in the worker nodes.

## Description
Implement a Docker/Kubernetes operator that runs containers, manages container lifecycle, and deploys to K8s clusters.

## Requirements
- **Docker Mode:**
  - Run containers from images with configurable commands and entrypoints
  - Manage container lifecycle (start, monitor, stop, cleanup)
  - Capture container logs (stdout/stderr) as job result
  - Support port mapping, volume mounts, and network configuration
  - Resource limits (CPU, memory) per container
- **Kubernetes Mode:**
  - Create Jobs or Pods in a target K8s cluster
  - Monitor pod status and capture logs
  - Support ConfigMaps and Secrets as environment variables
  - Clean up resources after job completion
- Support private image registries with authentication

## Acceptance Criteria
- [ ] Docker containers run with configurable images, commands, and resource limits
- [ ] Container logs are captured and stored as job result
- [ ] Port mapping, volume mounts, and network configuration work correctly
- [ ] K8s Jobs/Pods are created and monitored successfully
- [ ] K8s resources are cleaned up after job completion
- [ ] Private registry authentication works for both Docker and K8s

## Dependencies
- Issue 042 Pluggable Executor SPI
