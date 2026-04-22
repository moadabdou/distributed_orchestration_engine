# Docker Setup Guide

Fern-OS is designed to be easily deployed using Docker and Docker Compose. This ensures all components (Manager, Workers, Database, Storage) are correctly networked and configured.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) (20.10+)
- [Docker Compose](https://docs.docker.com/compose/install/) (v2.0+)

## Quick Start

To start the entire stack in the background:

```bash
docker compose up -d
```

To see logs:

```bash
docker compose logs -f
```

## Services Overview

The standard `docker-compose.yml` includes the following services:

| Service | Container Name | Port | Description |
| :--- | :--- | :--- | :--- |
| **Postgres** | `fernos-db` | `5433` | Primary metadata store for workflows and jobs. |
| **MinIO** | `fernos-minio` | `9000`, `9001` | Object storage for job data and artifacts. |
| **Manager** | `fernos-manager` | `8080`, `9090` | Orchestration engine core. HTTP (8080) and TCP (9090). |
| **Worker** | `fernos-worker` | - | Runs job tasks. Scales horizontally. |
| **Dashboard** | `fernos-dashboard` | `80` | Web UI for monitoring workflows. |

## Scaling Workers

One of Fern-OS's strengths is horizontal scalability. You can spin up multiple worker nodes with a single command:

```bash
docker compose up -d --scale worker=5
```

Each worker will automatically register with the Manager and start accepting jobs.

## Networking

All services are part of the `fernos-net` bridge network. Inside the network:
- Manager is reachable at `manager:9090` (TCP) and `manager:8080` (HTTP).
- MinIO is reachable at `minio:9000`.
- Postgres is reachable at `postgres:5432`.

From your host machine, use the ports mapped in `docker-compose.yml` (e.g., `5433` for Postgres).
