import time
import io
import pandas as pd
from fernos import events, bridge, FernMiniIO

# Initialize storage
storage = FernMiniIO()
client = storage.get_client()
bucket = storage.get_bucket()

def handle_clean_batch(data):
    batch_id = data.get("batch_id")
    filename = data.get("filename")
    
    bridge.log(f"Received clean batch {batch_id}: {filename}")
    
    # Download from MinIO
    try:
        response = client.get_object(bucket, filename)
        csv_data = response.read()
        response.close()
        response.release_conn()
        
        # Read into Pandas for final inspection
        df = pd.read_csv(io.BytesIO(csv_data))
        
        bridge.log(f"Loader received clean batch {batch_id} with {len(df)} rows.")
        bridge.log(f"Batch Stats: Mean={df['value'].mean():.2f}, Rows={len(df)}")
        
        # Simulate heavy lifting / IO
        time.sleep(0.5)
        # Cleanup: Remove the processed clean batch
        client.remove_object(bucket, filename)
        bridge.log(f"Loader: Successfully removed processed batch {filename} from storage.")
        
    except Exception as e:
        bridge.log(f"Error loading clean batch {batch_id}: {e}")

def start_loader():
    bridge.log("Loader waiting for notifications on 'clean_data_topic'...")
    
    # Use a shared counter to know when to stop
    loaded_count = [0]
    
    
    # Track processed files to avoid duplicates
    processed_files = set()
    
    def wrapped_callback(data):
        filename = data.get("filename")
        if filename and filename not in processed_files:
            handle_clean_batch(data)
            processed_files.add(filename)
            loaded_count[0] += 1
        elif filename:
            bridge.log(f"Loader: Skipping already processed file {filename}")

    # Step 1: Scan for existing files in MinIO
    bridge.log(f"Loader: Scanning bucket '{bucket}' for existing clean data...")
    try:
        objects = client.list_objects(bucket, prefix="clean_batch_", recursive=True)
        for obj in objects:
            filename = obj.object_name
            if filename not in processed_files:
                bridge.log(f"Loader: Found existing file {filename}, processing...")
                # Extract batch_id from filename: clean_batch_{id}_{timestamp}.csv
                parts = filename.split('_')
                if len(parts) >= 3:
                    batch_id = int(parts[2])
                    wrapped_callback({"batch_id": batch_id, "filename": filename})
    except Exception as e:
        bridge.log(f"Loader: Error scanning storage: {e}")

    # Step 2: Subscribe to clean data topic for new data
    bridge.log("Loader: Subscribing to 'clean_data_topic' for real-time updates...")
    events.on("clean_data_topic", wrapped_callback)
    
    # Listen until 10 batches are loaded
    try:
        while loaded_count[0] < 10:
            time.sleep(0.5)
        bridge.log("Loader processed 10 clean batches, shutting down.")
    except KeyboardInterrupt:
        bridge.log("Loader interrupted.")

if __name__ == "__main__":
    start_loader()
