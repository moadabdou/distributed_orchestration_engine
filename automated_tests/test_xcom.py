#!/usr/bin/env python3
import time
import json
import uuid
import sys
import os

# Add the current directory to sys.path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from test_workflows.api_client import clear_db, submit_workflow, get_workflow, get_job_logs, get_dag

def wait_for_workflow(workflow_id, timeout=60):
    start_time = time.time()
    while time.time() - start_time < timeout:
        wf = get_workflow(workflow_id)
        if wf and wf.get("status") in ["COMPLETED", "FAILED", "CANCELLED"]:
            return wf
        time.sleep(1)
    return None

def test_python_to_python_xcom():
    print("\n--- Test: Python to Python XCom ---")
    
    producer_script = """
import sys
import json
print('__FERN_CMD__Xcom:{"command": "push", "key": "secret_code", "value": "antigravity-42"}', flush=True)
ack = sys.stdin.readline()
print(f"Producer got ACK: {ack.strip()}", flush=True)
"""

    consumer_script = """
import sys
import json
print('__FERN_CMD__Xcom:{"command": "pull", "key": "secret_code"}', flush=True)
resp_json = sys.stdin.readline()
resp = json.loads(resp_json)
if resp.get('status') == 'SUCCESS':
    print(f"Consumer received: {resp['value']}", flush=True)
else:
    print(f"Consumer ERROR: {resp.get('status')} - {resp.get('error', 'Unknown error')}", flush=True)
"""

    jobs = [
        {"label": "producer", "payload": json.dumps({"type": "python", "script": producer_script})},
        {"label": "consumer", "payload": json.dumps({"type": "python", "script": consumer_script})}
    ]
    dependencies = [
        {"fromJobLabel": "producer", "toJobLabel": "consumer"}
    ]

    wf_id = submit_workflow(0, "XCom-Py-Py", jobs, dependencies)
    if not wf_id: return False

    wf = wait_for_workflow(wf_id)
    if not wf or wf["status"] != "COMPLETED":
        print(f"Test FAILED: Workflow status is {wf['status'] if wf else 'UNKNOWN'}")
        return False

    # Check consumer logs
    dag = get_dag(wf_id)
    # The consumer is the second job (index 1)
    consumer_job = next(j for j in dag["nodes"] if j["dagIndex"] == 1)
    logs = get_job_logs(consumer_job["jobId"])
    
    if "Consumer received: antigravity-42" in logs:
        print("SUCCESS: XCom value correctly exchanged.")
        return True
    else:
        print(f"FAIL: Expected value not found in logs. Logs:\n{logs}")
        return False

def test_bash_to_python_xcom():
    print("\n--- Test: Bash to Python XCom ---")
    
    producer_bash = """
echo '__FERN_CMD__Xcom:{"command": "push", "key": "bash_key", "value": "from-bash-with-love"}'
read ack <&0
echo \"Producer got ACK: $ack\"
"""

    consumer_py = """
import sys
import json
print('__FERN_CMD__Xcom:{"command": "pull", "key": "bash_key"}', flush=True)
resp_json = sys.stdin.readline()
resp = json.loads(resp_json)
if resp.get('status') == 'SUCCESS':
    print(f"Consumer received: {resp['value']}", flush=True)
else:
    print(f"Consumer ERROR: {resp.get('status')} - {resp.get('error', 'Unknown error')}", flush=True)
"""

    jobs = [
        {"label": "bash_producer", "payload": json.dumps({"type": "bash", "script": producer_bash})},
        {"label": "py_consumer", "payload": json.dumps({"type": "python", "script": consumer_py})}
    ]
    dependencies = [
        {"fromJobLabel": "bash_producer", "toJobLabel": "py_consumer"}
    ]

    wf_id = submit_workflow(1, "XCom-Bash-Py", jobs, dependencies)
    if not wf_id: return False

    wf = wait_for_workflow(wf_id)
    if not wf or wf["status"] != "COMPLETED":
        print("Test FAILED")
        return False

    dag = get_dag(wf_id)
    # The py_consumer is the second job (index 1)
    consumer_job = next(j for j in dag["nodes"] if j["dagIndex"] == 1)
    logs = get_job_logs(consumer_job["jobId"])
    
    if "Consumer received: from-bash-with-love" in logs:
        print("SUCCESS: XCom value correctly exchanged from Bash to Python.")
        return True
    else:
        print(f"FAIL: Expected value not found in logs. Logs:\n{logs}")
        return False

if __name__ == "__main__":
    clear_db()
    
    results = [
        test_python_to_python_xcom(),
        test_bash_to_python_xcom()
    ]
    
    print("\n" + "="*30)
    print(f"TEST SUMMARY: {sum(results)}/{len(results)} PASSED")
    print("="*30)
    
    if all(results):
        sys.exit(0)
    else:
        sys.exit(1)
