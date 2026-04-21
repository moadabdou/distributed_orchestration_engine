import time
import logging
import random
from fernos.worker.events import events

# Configure logging to show timestamps and levels
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("Generator")

def generate_data():
    logger.info("Generator starting up...")
    
    # Claim ownership of the raw data topic
    events.register("raw_data_topic")
    
    for i in range(1, 11):
        logger.info(f"Generating batch {i}/10...")
        
        # Simulate synthetic data generation
        batch_size = random.randint(50, 150)
        data = {
            "batch_id": i,
            "timestamp": time.time(),
            "values": [random.uniform(0, 100) for _ in range(batch_size)],
            "metadata": {
                "source": "synthetic_generator",
                "quality": random.choice(["high", "medium", "low"])
            }
        }
        
        # Blindly drop onto the topic
        logger.info(f"Producing batch {i} to 'raw_data_topic' (size: {batch_size})")
        events.emit("raw_data_topic", data)
        
        # Small delay between batches
        time.sleep(2)

    logger.info("Generator finished producing 10 batches.")

if __name__ == "__main__":
    generate_data()
