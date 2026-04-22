from fernos import (
    FernOSClient,
    DAG,
    PythonJob,
    ShellJob,
    SleepJob,
    EchoJob,
)


def build_order_fulfillment_dag() -> DAG:
    """Constructs and returns the order-fulfillment DAG."""

    with DAG(
        name="order_fulfillment_pipeline_v2",
        description="End-to-end order processing: validation, fraud, payment, shipping, and aggregation"
    ) as dag:

        validate = EchoJob(
            label="validate_order",
            data='{"order_id": "ORD-20260422", "customer": "acme_corp", "items": ["item_a", "item_b"]}',
        )

        inventory = PythonJob(
            label="inventory_check",
            script_path="order_pipeline/inventory_check.py",
            timeout_ms=60000,
        )

        fraud = PythonJob(
            label="fraud_scan",
            script_path="order_pipeline/fraud_scan.py",
            timeout_ms=60000,
        )

        payment_window = SleepJob(
            label="payment_window",
            ms=3000,
        )

        payment = PythonJob(
            label="process_payment",
            script_path="order_pipeline/process_payment.py",
            timeout_ms=120000,
            retry_count=1,
        )

        ship = ShellJob(
            label="ship_order",
            script=(
                'echo "Dispatching shipment for order ORD-20260422..."\n'
                'echo "Carrier: FernEx Priority"\n'
                'echo "Tracking: FRN-$(date +%s)"\n'
                'echo "Shipment dispatched successfully"'
            ),
            timeout_ms=30000,
        )

        receipt = PythonJob(
            label="send_receipt",
            script_path="order_pipeline/send_receipt.py",
            timeout_ms=30000,
        )

        aggregate = PythonJob(
            label="aggregate_results",
            script_path="order_pipeline/aggregate.py",
            timeout_ms=30000,
        )

        validate >> [inventory, fraud, payment_window] >> payment >> [ship, receipt] >> aggregate

        payment <= inventory
        payment <= fraud

    return dag


def main():
    client = FernOSClient("http://localhost:8080")

    print("--- FernOS Showcase: Order Fulfillment Pipeline ---\n")

    dag = build_order_fulfillment_dag()
    print(f"DAG '{dag.name}' defined with {len(dag.jobs)} jobs.\n")

    try:
        print("[SDK] Registering and executing workflow...")
        workflow = client.register_dag(dag)
        print(f"[SDK] Workflow live — ID: {workflow.id}")
        print(f"[SDK] Status: {workflow.status}\n")

        print("[SDK] Waiting for completion...")
        workflow.wait_for_completion(timeout_sec=120)
        print(f"[SDK] Final status: {workflow.status}\n")

        for job in workflow.list_jobs():
            print(f"--- {job.label} [{job.type}] ---")
            try:
                print(job.get_logs())
            except Exception:
                print("  (no logs available)")
            print()

        # Cleanup
        workflow.clear_xcom()
        workflow.delete()
        print("[SDK] Workflow cleaned up.")

    except Exception as e:
        print(f"\n[ERROR] {e}")
        print("Make sure the Fern-OS cluster is running (docker compose up -d --build)")


if __name__ == "__main__":
    main()
