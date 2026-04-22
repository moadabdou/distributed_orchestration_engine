from fernos import xcom
import time

# @fernos_include
from common_utils import get_storage

def consume():
    print("--- Consumer Operation ---")

    time.sleep(30)  # Simulate some startup delay

    handshake = xcom.pull("handshake")
    print(f"Received handshake: {handshake}")
    
    filename = xcom.pull("shared_file_key")
    if filename:
        storage = get_storage() # From common_utils.py
        client = storage.get_client()
        bucket = storage.get_bucket()
        
        print(f"Downloading {filename} from {bucket}")
        response = client.get_object(bucket, filename)
        print(f"Content: {response.read().decode('utf-8')}")
        response.close()
        response.release_conn()

if __name__ == "__main__":
    consume()
