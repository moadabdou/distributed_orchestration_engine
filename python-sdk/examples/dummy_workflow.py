from fernos import FernOSClient, DAG, PythonJob, ShellJob, SleepJob
import time

def run_dummy_example():
    # 1. Initialize the client
    # Assuming the Manager is running locally on port 8080
    client = FernOSClient("http://localhost:8080")

    print("--- Fern-OS SDK Dummy Example ---")

    # 2. Define the Workflow (DAG)
    # The 'with' statement creates a DAG context
    with DAG(name="dummy_pipeline", description="A simple pipeline to demo SDK features") as dag:
        
        # Define some jobs
        task_1 = ShellJob(
            label="prepare",
            script="echo 'Preparing environment...'; sleep 1"
        )

        task_2 = PythonJob(
            label="process_data",
            script="print('Processing complex data...'); import math; print(f'Result: {math.sqrt(144)}')"
        )

        task_3 = SleepJob(
            label="cooldown",
            ms=2000
        )

        task_4 = ShellJob(
            label="cleanup",
            script="echo 'Cleaning up...'"
        )

        # 3. Define the Control Flow (Dependencies)
        # Using the >> operator for a linear chain
        task_1 >> [task_2, task_3] >> task_4

    print(f"DAG '{dag.name}' defined with {len(dag.jobs)} jobs.")

    # 4. Register and Execute
    try:
        print("\n[SDK] Registering and starting workflow...")
        workflow = client.register_dag(dag)
        print(f"[SDK] Workflow registered successfully. ID: {workflow.id}")

        # 5. Monitor and Control
        print(f"[SDK] Current Status: {workflow.status}")

        # Let it run for a bit
        time.sleep(2)
        
        # Pause the workflow
        print("[SDK] Pausing workflow...")
        workflow.pause()
        print(f"[SDK] Status after pause: {workflow.status}")

        time.sleep(1)

        # Resume the workflow
        print("[SDK] Resuming workflow...")
        workflow.resume()
        print(f"[SDK] Status after resume: {workflow.status}")

        # Wait for completion
        print("[SDK] Waiting for workflow to reach terminal state...")
        workflow.wait_for_completion(timeout_sec=30)
        print(f"[SDK] Final Status: {workflow.status}")


        # Get logs for a specific job
        job = workflow.get_job("process_data")
        if job:
            print(f"\n[SDK] Logs for job '{job.label}':")
            print(f"\n--- Logs for {job.label} ---")
            print(job.get_logs())
            print("-------------------------")
        else:
            print("Job 'process_data' not found in workflow.")
        
        workflow.delete()
        print("\n[SDK] Workflow deleted successfully.")

    except Exception as e:
        print(f"\n[ERROR] Example failed: {e}")
        print("\nNote: This demo expects a Fern-OS Manager to be running at http://localhost:8080")

if __name__ == "__main__":
    run_dummy_example()
