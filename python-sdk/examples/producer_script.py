import io
import time
from fernos import xcom

# @fernos_include
from common_utils import get_storage

def produce():
    print("--- Producer Operation ---", flush=True)

    time.sleep(30)
    
    # 1. Push a simple message via XCom
    xcom.push("handshake", "Hello from producer!")
    
    # 2. Upload a file to MinIO using shared utility
    storage = get_storage() # From common_utils.py
    client = storage.get_client()
    bucket = storage.get_bucket()
    
    content = b"Shared dataset content"
    filename = "shared_dataset.txt"
    
    print(f"Uploading {filename} to {bucket}")
    client.put_object(bucket, filename, io.BytesIO(content), len(content))
    xcom.push("shared_file_key", filename)

if __name__ == "__main__":
    produce()
