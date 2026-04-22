import time
import random
import io
import pandas as pd
from fernos import events, bridge, FernMiniIO

def generate_data():
    bridge.log("Generator starting up...")
    
    # Initialize storage
    storage = FernMiniIO()
    client = storage.get_client()
    bucket = storage.get_bucket()
    
    # Claim ownership of the raw data topic
    events.register("raw_data_topic")
    
    bridge.log("Generator: Starting data generation.")

    for i in range(1, 11):
        bridge.log(f"Generating batch {i}/10...")
        
        # Simulate synthetic data generation
        batch_size = random.randint(50, 150)
        values = [random.uniform(0, 100) for _ in range(batch_size)]
        
        # Use Pandas for proper data formatting
        df = pd.DataFrame({
            "value": values,
            "timestamp": [time.time()] * batch_size,
            "batch_id": [i] * batch_size
        })
        
        # Convert to CSV
        csv_buffer = io.BytesIO()
        df.to_csv(csv_buffer, index=False)
        csv_data = csv_buffer.getvalue()
        
        # Upload to MinIO
        filename = f"raw_batch_{i}_{int(time.time())}.csv"
        bridge.log(f"Uploading {filename} to MinIO bucket '{bucket}'")
        client.put_object(
            bucket, 
            filename, 
            io.BytesIO(csv_data), 
            len(csv_data),
            content_type="text/csv"
        )
        
        # Notify via event system with the path
        events.emit("raw_data_topic", {"batch_id": i, "filename": filename})
        
        # Small delay between batches
        time.sleep(2)

    bridge.log("Generator finished producing 10 batches.")

if __name__ == "__main__":
    generate_data()
