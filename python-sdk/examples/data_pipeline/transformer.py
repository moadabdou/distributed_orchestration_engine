import time
import io
import pandas as pd
import numpy as np
from fernos import events, bridge, FernMiniIO

# Initialize storage globally or inside the handler
storage = FernMiniIO()
client = storage.get_client()
bucket = storage.get_bucket()

def process_batch(data):
    batch_id = data.get("batch_id")
    filename = data.get("filename")
    
    bridge.log(f"Received notification for batch {batch_id}: {filename}")
    
    # Download from MinIO
    try:
        response = client.get_object(bucket, filename)
        csv_data = response.read()
        response.close()
        response.release_conn()
        
        # Read into Pandas
        df = pd.read_csv(io.BytesIO(csv_data))
        bridge.log(f"Processing batch {batch_id} with {len(df)} rows...")
        
        # Apply Pandas/NumPy transformations
        df["normalized"] = (df["value"] - df["value"].mean()) / df["value"].std()
        df["clipped"] = np.clip(df["normalized"], -2, 2)
        
        # Export processed data to CSV
        out_buffer = io.BytesIO()
        df.to_csv(out_buffer, index=False)
        out_data = out_buffer.getvalue()
        
        # Upload back to Minio
        clean_filename = f"clean_batch_{batch_id}_{int(time.time())}.csv"
        client.put_object(
            bucket,
            clean_filename,
            io.BytesIO(out_data),
            len(out_data),
            content_type="text/csv"
        )
        
        bridge.log(f"Batch {batch_id} processed and uploaded as {clean_filename}")
        
        # Notify the Loader
        events.emit("clean_data_topic", {"batch_id": batch_id, "filename": clean_filename})
        
        # Cleanup: Remove the original raw batch
        client.remove_object(bucket, filename)
        bridge.log(f"Transformer: Successfully removed processed batch {filename} from storage.")
        
    except Exception as e:
        bridge.log(f"Error processing batch {batch_id}: {e}")

def start_transformer():
    bridge.log("Transformer waiting for data on 'raw_data_topic'...")
    
    # Claim ownership of the clean data topic
    events.register("clean_data_topic")
    
    # Use a shared counter to know when to stop
    processed_count = [0]
    
    
    # Track processed files to avoid duplicates
    processed_files = set()
    
    def wrapped_callback(data):
        filename = data.get("filename")
        if filename and filename not in processed_files:
            process_batch(data)
            processed_files.add(filename)
            processed_count[0] += 1
        elif filename:
            bridge.log(f"Transformer: Skipping already processed file {filename}")

    # Step 1: Scan for existing files in MinIO
    bridge.log(f"Transformer: Scanning bucket '{bucket}' for existing raw data...")
    try:
        objects = client.list_objects(bucket, prefix="raw_batch_", recursive=True)
        for obj in objects:
            filename = obj.object_name
            if filename not in processed_files:
                bridge.log(f"Transformer: Found existing file {filename}, processing...")
                # Extract batch_id from filename: raw_batch_{id}_{timestamp}.csv
                parts = filename.split('_')
                if len(parts) >= 3:
                    batch_id = int(parts[2])
                    wrapped_callback({"batch_id": batch_id, "filename": filename})
    except Exception as e:
        bridge.log(f"Transformer: Error scanning storage: {e}")

    # Step 2: Subscribe to raw data topic for new data
    bridge.log("Transformer: Subscribing to 'raw_data_topic' for real-time updates...")
    events.on("raw_data_topic", wrapped_callback)
    
    # Listen until 10 batches are processed
    try:
        while processed_count[0] < 10:
            bridge.log(f"Transformer has processed {processed_count[0]} batches so far...")
            time.sleep(0.5)
        bridge.log("Transformer processed 10 batches, shutting down.")
    except KeyboardInterrupt:
        bridge.log("Transformer interrupted.")

if __name__ == "__main__":
    start_transformer()
