import pytest
import json
from fernos import DAG, PythonJob

def test_dag_context_manager():
    with DAG(name="test_dag") as dag:
        PythonJob(label="job1", script="print(1)")
        PythonJob(label="job2", script="print(2)")
        
    assert len(dag.jobs) == 2
    labels = {j.label for j in dag.jobs}
    assert labels == {"job1", "job2"}
    assert dag.name == "test_dag"

def test_job_equality():
    j1 = PythonJob(label="job1", script="p1")
    j1_dup = PythonJob(label="job1", script="p1_different")
    j2 = PythonJob(label="job2", script="p2")
    
    assert j1 == j1_dup
    assert j1 != j2
    assert hash(j1) == hash(j1_dup)

def test_dag_set_behavior():
    with DAG(name="test_set") as dag:
        j1 = PythonJob(label="job1", script="p1")
        PythonJob(label="job1", script="p1_dup") # Duplicate label
        
    assert len(dag.jobs) == 1
    assert list(dag.jobs)[0].label == "job1"

def test_job_dependencies():
    with DAG(name="test_deps") as dag:
        job1 = PythonJob(label="job1", script="s1")
        job2 = PythonJob(label="job2", script="s2")
        job3 = PythonJob(label="job3", script="s3")
        
        job1 >> job2
        job2 >> job3
        
    assert "job1" in job2.upstream
    assert "job2" in job3.upstream
    assert len(job1.upstream) == 0

def test_duplicate_dependencies():
    with DAG(name="test_dups") as dag:
        j1 = PythonJob(label="j1", script="p1")
        j2 = PythonJob(label="j2", script="p2")
        
        # Multiple links should be idempotent
        j1 >> j2
        j1 >> j2
        j2 << j1
        
    assert len(j2.upstream) == 1
    assert "j1" in j2.upstream

def test_multi_dependency_chain():
    with DAG(name="test_chain") as dag:
        j1 = PythonJob(label="j1", script="p1")
        j2 = PythonJob(label="j2", script="p2")
        j3 = PythonJob(label="j3", script="p3")
        
        j1 >> j2 >> j3
        
    assert "j1" in j2.upstream
    assert "j2" in j3.upstream

def test_list_dependencies():
    with DAG(name="test_list") as dag:
        j1 = PythonJob(label="j1", script="p1")
        j2 = PythonJob(label="j2", script="p2")
        j3 = PythonJob(label="j3", script="p3")
        
        j1 >> [j2, j3]
        
    assert "j1" in j2.upstream
    assert "j1" in j3.upstream

def test_serialization():
    with DAG(name="test_data", description="My test DAG") as dag:
        job1 = PythonJob(label="downloader", script="down()")
        job2 = PythonJob(label="processor", script="proc()", timeout_ms=60000)
        job1 >> job2
        
    data = dag.to_dict()
    assert data["name"] == "test_data"
    assert len(data["jobs"]) == 2
    assert len(data["dependencies"]) == 1
    
    # Check job definition
    proc_job = next(j for j in data["jobs"] if j["label"] == "processor")
    assert proc_job["type"] == "python"
    payload = json.loads(proc_job["payload"])
    assert payload["script"] == "proc()"
    assert proc_job["timeoutMs"] == 60000
    
    # Check dependency edge
    edge = data["dependencies"][0]
    assert edge["fromJobLabel"] == "downloader"
    assert edge["toJobLabel"] == "processor"
