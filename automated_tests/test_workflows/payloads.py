import random
import json

TIMEOUT_THRESHOLD_MS = 60_000
NORMAL_SLEEP_RANGE = (5_000, 20_000)

PYTHON_SCRIPTS = [
    # 1. Simple but meaningful
    "import math; print(f'Circle area with r=5: {math.pi * 5**2:.2f}')",
    
    # 2. Recursion test (Fibonacci)
    "def fib(n): return n if n <= 1 else fib(n-1) + fib(n-2)\nprint(f'fib(20) = {fib(20)}')",
    
    # 3. Data Processing simulation
    "import statistics, random\ndata = [random.normalvariate(100, 15) for _ in range(500)]\n"
    "print(f'Stats: mean={statistics.mean(data):.2f}, stdev={statistics.stdev(data):.2f}')",
    
    # 4. Class and Logic
    "class Task:\n    def __init__(self, id): self.id = id\n    def run(self): print(f'Running task {self.id}')\n"
    "[Task(i).run() for i in range(5)]",
    
    # 5. Environment and System info
    "import os, sys, platform\nprint(f'OS: {platform.system()} {platform.release()}')\n"
    "print(f'Python: {sys.version.split()[0]}')\nprint(f'CWD: {os.getcwd()}')",
    
    # 6. JSON manipulation
    "import json\nd = {'status': 'active', 'metrics': {'cpu': 45, 'mem': 1024}}\n"
    "print(f'JSON dump: {json.dumps(d, indent=2)}')",
    
    # 7. List comprehensions and filtering
    "primes = [x for x in range(2, 50) if all(x % y != 0 for y in range(2, int(x**0.5) + 1))]\n"
    "print(f'Primes up to 50: {primes}')"
]

def make_payload(job_type, error_rate):
    if job_type == "mix":
        job_type = random.choice(["sleep", "python", "bash"])

    roll = random.random()
    is_error = roll < error_rate

    if job_type == "sleep":
        if is_error:
            error_kind = random.choice(["bad_arg", "timeout", "crash"])
            if error_kind == "bad_arg":
                return {"type": "sleep", "shit": str(random.randint(*NORMAL_SLEEP_RANGE))}, "bad_arg"
            elif error_kind == "timeout":
                return {"type": "sleep", "ms": str(random.randint(TIMEOUT_THRESHOLD_MS, TIMEOUT_THRESHOLD_MS * 3))}, "timeout"
            else: # crash = both
                return {"type": "sleep", "shit": str(random.randint(TIMEOUT_THRESHOLD_MS, TIMEOUT_THRESHOLD_MS * 3))}, "crash"
        return {"type": "sleep", "ms": str(random.randint(*NORMAL_SLEEP_RANGE))}, "ok"

    elif job_type == "python":
        script = random.choice(PYTHON_SCRIPTS)
        if is_error:
            # Inject error: invalid code
            return {"type": "python", "script": script + " syntax error here ###"}, "error"
        return {"type": "python", "script": script}, "ok"

    elif job_type == "bash":
        scripts = [
            "echo 'Running bash job'; date; uptime",
            "for i in {1..5}; do echo \"Iteration $i\"; sleep 0.1; done",
            "env | grep -E 'USER|PATH|PWD'",
            "df -h | head -n 5",
            "ps aux | head -n 5"
        ]
        script = random.choice(scripts)
        if is_error:
            # Inject error: non-zero exit code
            return {"type": "bash", "script": "ls /non_existent_directory_error_123"}, "error"
        return {"type": "bash", "script": script}, "ok"

    else:
        # Default to sleep
        return {"type": "sleep", "ms": "1000"}, "default"

def make_job(label, job_type, error_rate):
    payload, tag = make_payload(job_type, error_rate)
    return {"label": label, "payload": json.dumps(payload)}, tag
