import pytest
from fernos import DAG, ShellJob, JobGroup

def test_linear_chain():
    with DAG(name="test") as dag:
        j1 = ShellJob(label="j1", script="")
        j2 = ShellJob(label="j2", script="")
        j3 = ShellJob(label="j3", script="")
        
        j1 >> j2 >> j3
        
        assert "j1" in j2.upstream
        assert "j2" in j3.upstream

def test_list_chaining():
    with DAG(name="test") as dag:
        j1 = ShellJob(label="j1", script="")
        j2a = ShellJob(label="j2a", script="")
        j2b = ShellJob(label="j2b", script="")
        j3 = ShellJob(label="j3", script="")
        
        # Test Job >> List >> Job
        j1 >> [j2a, j2b] >> j3
        
        assert "j1" in j2a.upstream
        assert "j1" in j2b.upstream
        assert "j2a" in j3.upstream
        assert "j2b" in j3.upstream

def test_list_to_list_chaining():
    with DAG(name="test") as dag:
        j1a = ShellJob(label="j1a", script="")
        j1b = ShellJob(label="j1b", script="")
        j2a = ShellJob(label="j2a", script="")
        j2b = ShellJob(label="j2b", script="")
        
        # Test List >> List
        [j1a, j1b] >> [j2a, j2b]
        
        assert "j1a" in j2a.upstream
        assert "j1b" in j2a.upstream
        assert "j1a" in j2b.upstream
        assert "j1b" in j2b.upstream

def test_reverse_chaining():
    with DAG(name="test") as dag:
        j1 = ShellJob(label="j1", script="")
        j2 = ShellJob(label="j2", script="")
        
        j2 << j1
        assert "j1" in j2.upstream
        
        j3 = ShellJob(label="j3", script="")
        [j2, j3] << j1
        assert "j1" in j2.upstream
        assert "j1" in j3.upstream
