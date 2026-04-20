import pytest
import json
import os
from fernos import DAG, PythonJob, ShellJob, SleepJob, EchoJob, FibonacciJob

def test_python_job_serialization():
    with DAG(name="test-dag") as dag:
        p1 = PythonJob(
            label="py-task",
            script="print('hello')",
            args=["--verbose"],
            env={"DEBUG": "true"},
            timeout_ms=100000
        )
    
    data = p1.to_dict()
    assert data["label"] == "py-task"
    assert data["type"] == "python"
    assert data["timeoutMs"] == 100000
    
    payload = json.loads(data["payload"])
    assert payload["type"] == "python"
    assert payload["script"] == "print('hello')"
    assert payload["args"] == ["--verbose"]
    assert payload["env"] == {"DEBUG": "true"}

def test_shell_job_script_resolution(tmp_path):
    script_file = tmp_path / "myscript.sh"
    script_content = "echo 'hello from shell'"
    script_file.write_text(script_content)
    
    with DAG(name="test-shell-dag") as dag:
        s1 = ShellJob(label="sh-task", script_path=str(script_file))
    
    data = s1.to_dict()
    payload = json.loads(data["payload"])
    assert payload["type"] == "bash"
    assert payload["script"] == script_content

def test_sleep_job_serialization():
    with DAG(name="test-sleep") as dag:
        s1 = SleepJob(label="sleep-task", ms=5000)
    
    data = s1.to_dict()
    payload = json.loads(data["payload"])
    assert payload["type"] == "sleep"
    assert payload["ms"] == 5000
    # Timeout should be at least ms + 5000
    assert data["timeoutMs"] >= 10000

def test_echo_job_serialization():
    with DAG(name="test-echo") as dag:
        e1 = EchoJob(label="echo-task", data="hello world")
    
    data = e1.to_dict()
    payload = json.loads(data["payload"])
    assert payload["type"] == "echo"
    assert payload["data"] == "hello world"

def test_fibonacci_job_serialization():
    with DAG(name="test-fib") as dag:
        f1 = FibonacciJob(label="fib-task", n=10)
    
    data = f1.to_dict()
    payload = json.loads(data["payload"])
    assert payload["type"] == "fibonacci"
    assert payload["n"] == 10

def test_invalid_path_raises_error():
    with pytest.raises(ValueError, match="Failed to read script from path"):
        ShellJob(label="fail", script_path="/non/existent/path/script.sh")

def test_dag_serialization_with_specialized_jobs():
    with DAG(name="complex-dag") as dag:
        t1 = EchoJob(label="start", data="begin")
        t2 = PythonJob(label="process", script="print('process')")
        t3 = SleepJob(label="wait", ms=1000)
        
        t1 >> t2 >> t3
    
    dag_dict = dag.to_dict()
    assert dag_dict["name"] == "complex-dag"
    assert len(dag_dict["jobs"]) == 3
    assert len(dag_dict["dependencies"]) == 2
    
    # Verify 'type' is correctly set in jobs list for manager DTO
    types = {j["label"]: j["type"] for j in dag_dict["jobs"]}
    assert types["start"] == "echo"
    assert types["process"] == "python"
    assert types["wait"] == "sleep"
