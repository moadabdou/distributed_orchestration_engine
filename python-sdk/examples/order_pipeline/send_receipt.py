from fernos import xcom, bridge

bridge.log("Receipt Notification: starting")
receipt_path = xcom.pull("receipt_path")
status = xcom.pull("payment_status")

bridge.log(f"Payment status: {status}")
bridge.log(f"Receipt stored at: {receipt_path}")

xcom.push("receipt_sent", True)
bridge.log("Receipt Notification: customer notified")
