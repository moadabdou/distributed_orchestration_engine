import argparse
import subprocess
import urllib.request
import urllib.error
import json
import random
import time

def clear_db():
    print("Clearing database...")
    # NOTE: Using '-i' instead of '-it' since there's no TTY in script execution
    # Dropping and creating the public schema is a generic way to clear all tables.
    # If you only want to truncate specific tables, update the SQL command below.
    sql_command = "delete from jobs;"
    
    cmd = [
        "docker", "exec", "-i", "postgres-instance", 
        "psql", "-U", "root", "-d", "fernos", 
        "-c", sql_command
    ]
    try:
        subprocess.run(cmd, check=True)
        print("Database cleared successfully.")
    except subprocess.CalledProcessError as e:
        print(f"Failed to clear database. Error: {e}")

def submit_job(use_error=False):
    # Random sleep amount between 10,000 and 30,000 ms (10-30s)
    sleep_ms = random.randint(5000, 40000)
    
    # Outer JSON requires "payload" to be a stringified JSON object
    if use_error and random.random() < 0.10:
        payload_inner = {
            "type": "sleep",
            "wrong": str(sleep_ms)
        }
    else:
        payload_inner = {
            "type": "sleep",
            "ms": str(sleep_ms)
        }
    
    data = {
        "payload": json.dumps(payload_inner)
    }
    
    req_body = json.dumps(data).encode("utf-8")
    
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/jobs",
        data=req_body,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            resp_body = response.read().decode('utf-8')
            print(f"Submitted sleep job ({sleep_ms}ms) | Status: {response.status} | Response: {resp_body}")
    except urllib.error.URLError as e:
        print(f"Failed to submit job ({sleep_ms}ms). Error: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Automated tests for Distributed Orchestration Engine")
    parser.add_argument("-clear", action="store_true", help="Clear the database via Docker psql")
    parser.add_argument("--count", type=int, default=5, help="Number of sleep jobs to submit (default: 5)")
    parser.add_argument("-err", action="store_true", help="Inject wrong property in 10%% of jobs")
    
    args = parser.parse_args()
    
    if args.clear:
        clear_db()
        # Adding a small sleep to ensure DB is ready after schema recreation before sending requests
        time.sleep(1)
        
    print(f"Starting submission of {args.count} sleep jobs...")
    for i in range(args.count):
        submit_job(use_error=args.err)
        time.sleep(0.1) # slight delay to avoid hammering the server immediately
