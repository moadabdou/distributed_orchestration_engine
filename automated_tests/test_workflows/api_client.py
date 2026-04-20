import urllib.request
import urllib.error
import json
import subprocess
import time

def clear_db():
    print("Clearing database...")
    sql_command = "delete from jobs; delete from job_dependencies; delete from workflows; delete from xcoms;"
    cmd = [
        "docker", "exec", "-i", "fernos-db",
        "psql", "-U", "fernos_user", "-d", "fernos",
        "-c", sql_command,
    ]
    try:
        subprocess.run(cmd, check=True)
        print("Database cleared successfully.")
    except subprocess.CalledProcessError as e:
        print(f"Failed to clear database. Error: {e}")

def get_dag(workflow_id):
    req = urllib.request.Request(f"http://localhost:8080/api/v1/workflows/{workflow_id}/dag")
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"  [ERR] Get DAG for workflow {workflow_id} | {e}")
        return None

def execute_workflow(workflow_id):
    req = urllib.request.Request(
        f"http://localhost:8080/api/v1/workflows/{workflow_id}/execute",
        data=b"",
        headers={"Content-Length": "0"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            tag = "OK" if status == 200 else "FAIL"
            print(f"  [{tag}] Execute workflow {workflow_id} | HTTP {status}")
    except urllib.error.URLError as e:
        print(f"  [ERR] Execute workflow {workflow_id} | {e}")

def submit_workflow(index, name, jobs, dependencies):
    data = {
        "name": name,
        "jobs": jobs,
        "dependencies": dependencies,
    }
    req_body = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/workflows",
        data=req_body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as response:
            resp_json = json.loads(response.read().decode("utf-8"))
            workflow_id = resp_json.get("id")
            print(
                f"[WF-{index}] jobs={len(jobs)} "
                f"deps={len(dependencies)} | "
                f"HTTP {response.status} | id={workflow_id}"
            )
            if workflow_id:
                execute_workflow(workflow_id)
            return workflow_id
    except urllib.error.URLError as e:
        print(f"[WF-{index}] Submit FAILED: {e}")
        return None
def get_workflow(workflow_id):
    req = urllib.request.Request(f"http://localhost:8080/api/v1/workflows/{workflow_id}")
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"  [ERR] Get workflow {workflow_id} | {e}")
        return None

def get_job_logs(job_id):
    req = urllib.request.Request(f"http://localhost:8080/api/v1/logs/jobs/{job_id}")
    try:
        with urllib.request.urlopen(req) as response:
            return response.read().decode("utf-8")
    except Exception as e:
        print(f"  [ERR] Get job logs {job_id} | {e}")
        return ""

def clear_xcom_history(workflow_id):
    req = urllib.request.Request(
        f"http://localhost:8080/api/v1/workflows/{workflow_id}/xcom",
        method="DELETE"
    )
    try:
        with urllib.request.urlopen(req) as response:
            print(f"  [OK] Clear XCom history for workflow {workflow_id} | HTTP {response.status}")
            return True
    except Exception as e:
        print(f"  [ERR] Clear XCom history for workflow {workflow_id} | {e}")
        return False
