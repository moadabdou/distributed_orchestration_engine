import time
from fernos import FernOSClient, DAG, PythonJob

def run_xcom_storage_example():
    # 1. Initialize the client
    # Assuming the Manager is running locally on port 8080
    client = FernOSClient("http://localhost:8080")

    print("--- Fern-OS XCom & Storage Integration Example ---")

    # 2. Define the Workflow (DAG)
    with DAG(name="xcom_storage_demo", description="Demonstrates XCom and MinIO shared storage") as dag:
        
        # Producer Job: Uses script_path pointing to producer_script.py
        producer = PythonJob(
            label="producer",
            script_path="examples/producer_script.py"
        )

        # Consumer Job: Uses script_path pointing to consumer_script.py
        consumer = PythonJob(
            label="consumer",
            script_path="examples/consumer_script.py"
        )

        # 3. Define the Dependency
        producer >> consumer

    print(f"DAG '{dag.name}' defined.")

    # 4. Register and Execute
    try:
        print("\n[SDK] Registering and starting workflow...")
        workflow = client.register_dag(dag)
        print(f"[SDK] Workflow registered. ID: {workflow.id}")

        time.sleep(10)  # Let it start

        workflow.pause()
        print(f"[SDK] Workflow paused. Status: {workflow.status}")
        time.sleep(5)
        workflow.resume()
        print(f"[SDK] Workflow resumed. Status: {workflow.status}")

        # 5. Wait for completion
        print("[SDK] Waiting for completion...")
        workflow.wait_for_completion(timeout_sec=120)
        print(f"[SDK] Final Status: {workflow.status}")

        # 6. Display Logs
        for label in ["producer", "consumer"]:
            job = workflow.get_job(label)
            if job:
                print(f"\n--- Logs for {label} ---")
                print(job.get_logs())
                print("-" * 25)

        # 7. Cleanup (per user request: "clean the history and workflow after that")
        print("\n[SDK] Cleaning up workflow and history...")
        workflow.clear_xcom()
        workflow.delete()
        print("[SDK] Cleanup completed.")

    except Exception as e:
        print(f"\n[ERROR] Example failed: {e}")

if __name__ == "__main__":
    run_xcom_storage_example()
