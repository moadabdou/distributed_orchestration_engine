# Environment Variables

Fern-OS uses environment variables for configuration. You should create a `.env` file in the root directory by copying `.env.example`.

## Core Configuration

| Variable | Default | Description |
| :--- | :--- | :--- |
| `JWT_SECRET` | (Required) | Secret key used to sign and verify JWT tokens for job and worker authentication. |
| `WORKER_AUTH_TOKEN` | (Optional) | If set, workers must provide this token to register with the manager. |
| `DB_PASSWORD` | `fernos_pass` | Password for the PostgreSQL database. |

## MinIO (Object Storage)

| Variable | Default | Description |
| :--- | :--- | :--- |
| `MINIO_ROOT_USER` | `admin` | MinIO access key. |
| `MINIO_ROOT_PASSWORD` | `password123` | MinIO secret key. |
| `MINIO_ENDPOINT` | `http://minio:9000` | Endpoint for internal service communication. |
| `MINIO_BUCKET` | `fernos-storage` | Default bucket for storage. |

## Advanced Manager/Worker Config

These can be set to override default behaviors:

| Variable | Description |
| :--- | :--- |
| `MANAGER_HOST` | Hostname of the manager (used by workers). |
| `MANAGER_PORT` | TCP port of the manager (default `9090`). |
| `MANAGER_HTTP_PORT` | HTTP port of the manager (default `8080`). |
| `WORKER_HEARTBEAT_INTERVAL_MS` | Frequency of worker status updates (default `5000`). |

> [!IMPORTANT]
> When running with Docker Compose, most of these are pre-configured in the `docker-compose.yml`. You only need to modify the `.env` file if you want to change secrets or specific backend settings.
