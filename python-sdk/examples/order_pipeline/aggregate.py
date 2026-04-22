from fernos import xcom, bridge

bridge.log("Aggregator: collecting results from all stages")

order = xcom.pull("validate_order")
stock = xcom.pull("stock_snapshot")
fraud = xcom.pull("fraud_result")
payment = xcom.pull("payment_status")
receipt = xcom.pull("receipt_path")
receipt_ok = xcom.pull("receipt_sent")

summary = {
    "order_received": order is not None,
    "stock_verified": stock is not None,
    "fraud_checked": fraud is not None,
    "payment_status": payment,
    "receipt_persisted": receipt is not None,
    "receipt_notified": receipt_ok,
}

bridge.log(f"Pipeline summary: {summary}")
bridge.log("Aggregator: workflow complete")
