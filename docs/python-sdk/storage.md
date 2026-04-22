# Storage with Fern-OS

Fern-OS provides an integrated storage abstraction called `FernMiniIO`. This is a wrapper around the [Minio](https://min.io/) Python client, allowing you to easily store and retrieve large files (like datasets or models) directly from your jobs.

## Configuration

`FernMiniIO` automatically configures itself using environment variables provided by the Fern-OS Manager:

| Variable | Description |
|----------|-------------|
| `MINIO_ENDPOINT` | The URL of the MinIO server. |
| `MINIO_ACCESS_KEY` | Access key for authentication. |
| `MINIO_SECRET_KEY` | Secret key for authentication. |
| `MINIO_BUCKET` | The default bucket assigned to the workflow. |

## Usage

To use storage, import `FernMiniIO` and initialize it.

```python
from fernos import FernMiniIO
import io

# 1. Initialize storage
storage = FernMiniIO()

# 2. Get the underlying Minio client
client = storage.get_client()

# 3. Get the default bucket name
bucket = storage.get_bucket()

# 4. Use the Minio client to perform operations
data = b"Hello from Fern-OS Storage!"
client.put_object(
    bucket, 
    "greeting.txt", 
    io.BytesIO(data), 
    len(data),
    content_type="text/plain"
)
```

## Reading Data

You can retrieve data using the standard Minio client methods.

```python
response = client.get_object(bucket, "greeting.txt")
try:
    content = response.read()
    print(f"File content: {content.decode()}")
finally:
    response.close()
    response.release_conn()
```

## Best Practices for Large Data

- **Paths via XCom/Events**: When a job produces a file, it should upload it to MinIO and then pass the *filename* or *path* to the next job via [XCom](xcom.md) or [Events](events.md).
- **Resource Management**: Always close and release connections when reading from MinIO to avoid resource leaks.
- **Bucket Usage**: Always use `storage.get_bucket()` instead of hardcoding bucket names, as the Manager might assign different buckets for different workflows or environments.
