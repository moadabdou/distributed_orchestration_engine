import hashlib
import time
from fernos import xcom, bridge

bridge.log("Fraud Scan: analysing order")
time.sleep(1)  # simulate ML model inference latency

# Pull the original order payload from the validation step
order_raw = xcom.pull("validate_order")
order_hash = hashlib.sha256((order_raw or "").encode()).hexdigest()[:12]

risk_score = 0.07  # low-risk for demo purposes
result = {"order_hash": order_hash, "risk_score": risk_score, "flagged": risk_score > 0.5}

xcom.push("fraud_result", result)
bridge.log(f"Fraud Scan: risk_score={risk_score}, flagged={result['flagged']}")
