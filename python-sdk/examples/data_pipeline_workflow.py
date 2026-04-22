import os
from fernos import DAG, PythonJob

def create_data_pipeline_workflow():
    # Define a unique workflow name
    dag = DAG("producer_consumer_pipeline_v1")

    # Define the 3 jobs
    # We use PythonJob which points to the scripts we just created
    
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


    # In this event-driven pipeline, there are no strict DAG dependencies 
    # in terms of "Execution Order" because they need to be active together.
    # However, we can just add them to the DAG.
    dag.add_job(generator)
    dag.add_job(transformer)
    dag.add_job(loader)

    # Note: In Fern-OS, if jobs have no dependencies, they start as soon 
    # as capacity is available. In this example, they will all start 
    # and communicate via the event system.

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
