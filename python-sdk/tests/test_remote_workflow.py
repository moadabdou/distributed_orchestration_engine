import pytest
from unittest.mock import MagicMock, patch
from uuid import uuid4
from fernos import FernOSClient, RemoteWorkflow, RemoteJob
import requests

@pytest.fixture
def client():
    return FernOSClient("http://localhost:8080")

@pytest.fixture
def workflow_id():
    return uuid4()

def test_remote_workflow_list_jobs(client, workflow_id):
    mock_data = {
        "content": [
            {"id": str(uuid4()), "label": "job1", "status": "PENDING", "type": "python", "payload": "{}"},
            {"id": str(uuid4()), "label": "job2", "status": "COMPLETED", "type": "bash", "payload": "{}"}
        ],
        "totalElements": 2
    }
    
    with patch.object(client.session, 'request') as mock_req:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.text = '{"content": [{"id": "...", "label": "job1", "status": "PENDING", "type": "python", "payload": "{}"}]}'
        mock_resp.json.return_value = {"content": [
            {"id": str(uuid4()), "label": "job1", "status": "PENDING", "type": "python", "payload": "{}"}
        ]}
        mock_req.return_value = mock_resp
        
        wf = RemoteWorkflow(client, {"id": str(workflow_id), "name": "test", "status": "DRAFT"})
        jobs = wf.list_jobs()
        
        assert len(jobs) == 1
        assert jobs[0].label == "job1"
        
        # Verify the call
        args, kwargs = mock_req.call_args
        assert args[0] == "GET"
        assert f"/api/v1/workflows/{workflow_id}/jobs" in args[1]

def test_remote_workflow_get_job(client, workflow_id):
    job_id = uuid4()
    mock_job = {"id": str(job_id), "label": "task-A", "status": "RUNNING", "type": "python", "payload": "{}"}
    
    with patch.object(client.session, 'request') as mock_req:
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = mock_job
        mock_req.return_value = mock_resp
        
        wf = RemoteWorkflow(client, {"id": str(workflow_id), "name": "test", "status": "DRAFT"})
        job = wf.get_job("task-A")
        
        assert job is not None
        assert job.label == "task-A"
        assert job.id == job_id
        
        # Verify the call
        args, kwargs = mock_req.call_args
        assert args[0] == "GET"
        assert f"/api/v1/workflows/{workflow_id}/jobs/task-A" in args[1]

def test_remote_workflow_get_job_not_found(client, workflow_id):
    with patch.object(client.session, 'request') as mock_req:
        mock_resp = MagicMock()
        mock_resp.status_code = 404
        # Simulate raise_for_status
        mock_resp.raise_for_status.side_effect = requests.exceptions.HTTPError(response=mock_resp)
        mock_req.return_value = mock_resp
        
        wf = RemoteWorkflow(client, {"id": str(workflow_id), "name": "test", "status": "DRAFT"})
        job = wf.get_job("non-existent")
        
        assert job is None
        
        # Verify the call
        args, kwargs = mock_req.call_args
        assert args[0] == "GET"
        assert f"/api/v1/workflows/{workflow_id}/jobs/non-existent" in args[1]
