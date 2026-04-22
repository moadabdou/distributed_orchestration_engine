import os
from fernos import DAG, PythonJob

def create_data_pipeline_workflow():
    # Define a unique workflow name
    with DAG("producer_consumer_pipeline_v1") as dag:
        # Define the 3 jobs
        # 1. Generator - The Producer
        generator = PythonJob(
            label="generator",
            script_path="data_pipeline/generator.py",
            timeout_ms=300000 # 5 minutes
        )

        # 2. Transformer - The Processor
        transformer = PythonJob(
            label="transformer",
            script_path="data_pipeline/transformer.py",
            timeout_ms=300000
        )

        # 3. Loader - The Consumer
        loader = PythonJob(
            label="loader",
            script_path="data_pipeline/loader.py",
            timeout_ms=300000
        )

        # In this event-driven pipeline, jobs communicate via signaling (Data Flow).
        # We define these relationships using the <= operator or .signals() method.
        transformer <= generator
        loader <= transformer

    # Note: In Fern-OS, if jobs have no control dependencies (>>), they can 
    # start as soon as capacity is available. The signaling edges (Data Flow)
    # provide a visual representation of how data flows through the pipeline.

    return dag

if __name__ == "__main__":
    from fernos import FernOSClient
    
    # Configuration from environment or defaults (FERNOS_MANAGER_HOST, FERNOS_MANAGER_HTTP_PORT)
    client = FernOSClient()

    
    pipeline = create_data_pipeline_workflow()
    
    try:
        print(f"Deploying data pipeline workflow to {client.base_url}...")
        response = client.register_dag(pipeline)
        print(f"Workflow created successfully! ID: {response.id}")

        response.wait_for_completion(5*300000) 
    except Exception as e:
        print(f"Failed to create workflow: {e}")
