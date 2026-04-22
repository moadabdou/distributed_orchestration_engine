import pytest
from fernos.core import DAG, EchoJob, JobGroup

def test_data_flow_operator_syntax():
    with DAG("test_data_flow") as dag:
        j1 = EchoJob("j1", "data1")
        j2 = EchoJob("j2", "data2")
        
        # Signaling syntax: j1 is signaled by j2
        j1 <= j2
        
    dag_dict = dag.to_dict()
    assert len(dag_dict["dataDependencies"]) == 1
    dep = dag_dict["dataDependencies"][0]
    assert dep["fromJobLabel"] == "j2"
    assert dep["toJobLabel"] == "j1"
    # Control flow should be empty
    assert len(dag_dict["dependencies"]) == 0

def test_data_flow_method_syntax():
    with DAG("test_data_flow_method") as dag:
        j1 = EchoJob("j1", "data1")
        j2 = EchoJob("j2", "data2")
        
        # Signaling method: j1 signals j2
        j1.signals(j2)
        
    dag_dict = dag.to_dict()
    assert len(dag_dict["dataDependencies"]) == 1
    dep = dag_dict["dataDependencies"][0]
    assert dep["fromJobLabel"] == "j1"
    assert dep["toJobLabel"] == "j2"

def test_data_flow_group_syntax():
    with DAG("test_data_flow_group") as dag:
        j1 = EchoJob("j1", "data1")
        j2 = EchoJob("j2", "data2")
        j3 = EchoJob("j3", "data3")
        
        # Group signaled by single job
        JobGroup([j1, j2]) <= j3
        
    dag_dict = dag.to_dict()
    assert len(dag_dict["dataDependencies"]) == 2
    labels = [d["toJobLabel"] for d in dag_dict["dataDependencies"]]
    assert "j1" in labels
    assert "j2" in labels
    assert all(d["fromJobLabel"] == "j3" for d in dag_dict["dataDependencies"])

def test_data_flow_mixed_with_control():
    with DAG("test_mixed") as dag:
        j1 = EchoJob("j1", "data1")
        j2 = EchoJob("j2", "data2")
        
        j1 >> j2 # Control: j1 before j2
        j2 <= j1 # Data: j2 signaled by j1 (redundant but possible)
        
    dag_dict = dag.to_dict()
    assert len(dag_dict["dependencies"]) == 1
    assert len(dag_dict["dataDependencies"]) == 1
    
    assert dag_dict["dependencies"][0] == {"fromJobLabel": "j1", "toJobLabel": "j2"}
    assert dag_dict["dataDependencies"][0] == {"fromJobLabel": "j1", "toJobLabel": "j2"}
