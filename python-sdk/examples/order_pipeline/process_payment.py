import io
import json
import time
from fernos import xcom, bridge, events, FernMiniIO

bridge.log("Payment Processor: starting")

# Pull results from upstream via XCom
stock = xcom.pull("stock_snapshot")
fraud = xcom.pull("fraud_result")

bridge.log(f"Stock data received: {stock}")
bridge.log(f"Fraud result received: {fraud}")

if fraud and fraud.get("flagged"):
    bridge.log("Payment BLOCKED — order flagged by fraud scan")
    xcom.push("payment_status", "BLOCKED")
else:
    # Process the payment
    receipt = {
        "status": "PAID",
        "amount": 149.99,
        "currency": "USD",
        "stock_verified": stock is not None,
        "timestamp": time.time(),
    }

    # Persist receipt to MinIO
    storage = FernMiniIO()
    client = storage.get_client()
    bucket = storage.get_bucket()

    receipt_bytes = json.dumps(receipt, indent=2).encode()
    filename = f"receipts/order_receipt_{int(time.time())}.json"

    client.put_object(bucket, filename, io.BytesIO(receipt_bytes), len(receipt_bytes),
                      content_type="application/json")
    bridge.log(f"Receipt written to MinIO: {filename}")

    # Push payment status and file reference via XCom
    xcom.push("payment_status", "PAID")
    xcom.push("receipt_path", filename)

    # Emit a real-time event so any listener gets notified immediately
    events.register("payment_completed")
    events.emit("payment_completed", {"receipt": filename, "amount": receipt["amount"]})
    bridge.log("Payment Processor: done")
