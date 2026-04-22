from typing import List, Optional, Union, Dict, Any, TYPE_CHECKING
import threading
import json
import re
import os
import time
import requests
from uuid import UUID
from .compiler import ScriptPreprocessor

if TYPE_CHECKING:
    from .core import DAG

class JobGroup(list):
    """A list-like collection of jobs that supports operator overloading for DAG definition."""
    def __rshift__(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        for job in self:
            job >> other
        return other

    def __lshift__(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        for job in self:
            job << other
        return other

    def __le__(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        for job in self:
            job <= other
        return other

    def signals(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        for job in self:
            job.signals(other)
        return other

class DAGContext(threading.local):
    """Thread-local storage for the active DAG context."""
    active_dag: Optional['DAG'] = None

_context = DAGContext()

def _get_current_dag() -> Optional['DAG']:
    """Retrieves the current active DAG from the thread-local context."""
    return _context.active_dag

def _set_current_dag(dag: Optional['DAG']):
    """Sets the active DAG in the thread-local context."""
    _context.active_dag = dag

class Job:
    """
    Base class for all Fern-OS jobs.
    """
    def __init__(self, label: str, timeout_ms: int = 300000, retry_count: int = 0):
        self.label = label
        self.timeout_ms = timeout_ms
        self.retry_count = retry_count
        self.upstream: set[str] = set()
        self.data_upstream: set[str] = set()
        
        # Register with the current DAG context if available
        current_dag = _get_current_dag()
        if current_dag:
            current_dag.add_job(self)

    def _resolve_script(self, script: Optional[str], path: Optional[str]) -> str:
        """Resolves script content from either raw string or file path, handling includes."""
        content = ""
        base_path = os.getcwd()
        
        if script:
            content = script
        elif path:
            try:
                # Try relative to caller's file or absolute
                with open(path, 'r') as f:
                    content = f.read()
                base_path = os.path.dirname(os.path.abspath(path))
            except Exception as e:
                raise ValueError(f"Failed to read script from path '{path}': {e}")
        
        seen = set()
        if path:
            seen.add(os.path.abspath(path))
            
        if self._type == "python":
            return ScriptPreprocessor(base_path).process(content)
            
        return self._resolve_includes(content, base_path, seen)

    def _resolve_includes(self, content: str, base_path: str, seen: Optional[set[str]] = None) -> str:
        """Recursively resolves # @fernos_include directives."""
        if seen is None:
            seen = set()

        # Pattern: # @fernos_include followed by a newline and import path
        # Using [^\s\n]+ to match the path string
        pattern = r'#\s*@fernos_include\s*\n\s*import\s+([^\s\n]+)'

        def replacer(match):
            include_path = match.group(1)
            # Resolve to absolute path relative to base_path
            if not os.path.isabs(include_path):
                abs_include_path = os.path.abspath(os.path.join(base_path, include_path))
            else:
                abs_include_path = os.path.abspath(include_path)

            if abs_include_path in seen:
                return f"# Circular include detected: {include_path}"

            seen.add(abs_include_path)

            try:
                with open(abs_include_path, 'r') as f:
                    included_content = f.read()
                # Recursively resolve includes in the included file
                return self._resolve_includes(included_content, os.path.dirname(abs_include_path), seen)
            except Exception as e:
                return f"# Failed to include {include_path}: {e}"

        return re.sub(pattern, replacer, content)

    def _get_payload(self) -> Dict[str, Any]:
        """Subclasses must override this to provide their specific payload."""
        raise NotImplementedError("Subclasses must implement _get_payload")

    @property
    def _type(self) -> str:
        """Subclasses must define their job type."""
        raise NotImplementedError("Subclasses must implement _type")

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Job):
            return NotImplemented
        return self.label == other.label

    def __hash__(self) -> int:
        return hash(self.label)

    def __rshift__(self, other: Union['Job', List['Job']]) -> Union['Job', 'JobGroup']:
        if isinstance(other, list):
            other = JobGroup(other)
            for job in other:
                job.upstream.add(self.label)
        else:
            other.upstream.add(self.label)
        return other

    def __lshift__(self, other: Union['Job', List['Job']]) -> Union['Job', 'JobGroup']:
        if isinstance(other, list):
            other = JobGroup(other)
            for job in other:
                self.upstream.add(job.label)
        else:
            self.upstream.add(other.label)
        return other

    def __le__(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        if isinstance(other, (list, JobGroup)):
            for job in other:
                self.data_upstream.add(job.label)
        else:
            self.data_upstream.add(other.label)
        return self

    def signals(self, other: Union['Job', List['Job'], 'JobGroup']) -> Union['Job', 'JobGroup']:
        if isinstance(other, (list, JobGroup)):
            for job in other:
                job.data_upstream.add(self.label)
        else:
            other.data_upstream.add(self.label)
        return other

    def to_dict(self) -> Dict[str, Any]:
        """Serializes the job to backend format, embedding type in payload."""
        payload_data = self._get_payload()
        payload_data["type"] = self._type
        
        return {
            "label": self.label,
            "type": self._type,
            "payload": json.dumps(payload_data),
            "timeoutMs": self.timeout_ms,
            "retryCount": self.retry_count
        }

class PythonJob(Job):
    """Executes a Python script or notebook."""
    _type = "python"
    
    def __init__(self, label: str, script: Optional[str] = None, script_path: Optional[str] = None, 
                 args: Optional[List[str]] = None, env: Optional[Dict[str, str]] = None,
                 venv: Optional[str] = None, conda_env: Optional[str] = None, 
                 timeout_ms: int = 300000, retry_count: int = 0):
        super().__init__(label, timeout_ms, retry_count)
        self.script_content = self._resolve_script(script, script_path)
        self.args = args or []
        self.env = env or {}
        self.venv = venv
        self.conda_env = conda_env

    def _get_payload(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"script": self.script_content}
        if self.args: payload["args"] = self.args
        if self.env: payload["env"] = self.env
        if self.venv: payload["venv"] = self.venv
        if self.conda_env: payload["conda_env"] = self.conda_env
        return payload

class ShellJob(Job):
    """Executes a bash script."""
    _type = "bash"
    
    def __init__(self, label: str, script: Optional[str] = None, script_path: Optional[str] = None, 
                 timeout_ms: int = 300000, retry_count: int = 0):
        super().__init__(label, timeout_ms, retry_count)
        self.script_content = self._resolve_script(script, script_path)

    def _get_payload(self) -> Dict[str, Any]:
        return {"script": self.script_content}

class SleepJob(Job):
    """Sleeps for a specified duration."""
    _type = "sleep"
    
    def __init__(self, label: str, ms: int, timeout_ms: int = 600000, retry_count: int = 0):
        # Default timeout for sleep is ms + buffer, or user defined
        timeout = max(timeout_ms, ms + 5000)
        super().__init__(label, timeout, retry_count)
        self.ms = ms

    def _get_payload(self) -> Dict[str, Any]:
        return {"ms": self.ms}

class EchoJob(Job):
    """Returns the input data."""
    _type = "echo"
    
    def __init__(self, label: str, data: str, timeout_ms: int = 30000, retry_count: int = 0):
        super().__init__(label, timeout_ms, retry_count)
        self.data = data

    def _get_payload(self) -> Dict[str, Any]:
        return {"data": self.data}

class FibonacciJob(Job):
    """Calculates Fibonacci sequence up to n."""
    _type = "fibonacci"
    
    def __init__(self, label: str, n: int, timeout_ms: int = 60000, retry_count: int = 0):
        super().__init__(label, timeout_ms, retry_count)
        self.n = n

    def _get_payload(self) -> Dict[str, Any]:
        return {"n": self.n}

class DAG:
    """
    Context manager for defining Fern-OS workflows.
    
    Attributes:
        name (str): The human-readable name of the workflow.
        description (str): A brief description of the workflow.
    """
    def __init__(self, name: str, description: str = ""):
        self.name = name
        self.description = description
        self.jobs: set[Job] = set()
        self._prev_dag: Optional[DAG] = None

    def __enter__(self):
        """Activates the DAG context."""
        self._prev_dag = _get_current_dag()
        _set_current_dag(self)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Deactivates the DAG context."""
        _set_current_dag(self._prev_dag)

    def add_job(self, job: Job):
        """Adds a job to the DAG if it doesn't already exist."""
        self.jobs.add(job)

    def to_dict(self) -> Dict[str, Any]:
        """
        Serializes the entire DAG into the format expected by CreateWorkflowRequest.
        """
        jobs_data = [job.to_dict() for job in self.jobs]
        
        dependencies = []
        data_dependencies = []
        for job in self.jobs:
            for up in job.upstream:
                dependencies.append({
                    "fromJobLabel": up,
                    "toJobLabel": job.label
                })
            for up in job.data_upstream:
                data_dependencies.append({
                    "fromJobLabel": up,
                    "toJobLabel": job.label
                })
                
        return {
            "name": self.name,
            "jobs": jobs_data,
            "dependencies": dependencies,
            "dataDependencies": data_dependencies
        }

class RemoteJob:
    """A wrapper for a job that exists on the Fern-OS Manager."""
    def __init__(self, client: 'FernOSClient', workflow_id: UUID, data: Dict[str, Any]):
        self._client = client
        self._workflow_id = workflow_id
        self._data = data

    @property
    def id(self) -> UUID:
        return UUID(self._data["id"])

    @property
    def label(self) -> str:
        return self._data["label"]

    @property
    def status(self) -> str:
        # Refresh data to get latest status
        self.refresh()
        return self._data["status"]

    @property
    def type(self) -> str:
        #type is merged to payload 
        try:
            payload = json.loads(self._data["payload"])
        except:
            return "unknown"
        return payload.get("type", "unknown")

    @property
    def payload(self) -> str:
        return self._data["payload"]

    @property
    def worker_id(self) -> Optional[str]:
        return self._data.get("workerId")

    def refresh(self):
        """Fetches the latest job data from the manager."""
        self._data = self._client._get(f"/api/v1/jobs/{self.id}")

    def cancel(self) -> 'RemoteJob':
        """Cancels the job."""
        self._data = self._client._post(f"/api/v1/jobs/{self.id}/cancel")
        return self

    def retry(self) -> 'RemoteJob':
        """Retries the job."""
        self._data = self._client._post(f"/api/v1/jobs/{self.id}/retry")
        return self

    def get_logs(self, raw: bool = True, start: Optional[int] = None, length: Optional[int] = None) -> str:
        """Retrieves job logs."""
        endpoint = f"/api/v1/logs/jobs/{self.id}/raw" if raw else f"/api/v1/logs/jobs/{self.id}"
        params = {}
        if start is not None: params["start"] = start
        if length is not None: params["length"] = length
        return self._client._get(endpoint, params=params, is_json=not raw)

class RemoteWorkflow:
    """A wrapper for a workflow that exists on the Fern-OS Manager."""
    def __init__(self, client: 'FernOSClient', data: Dict[str, Any]):
        self._client = client
        self._data = data

    @property
    def id(self) -> UUID:
        return UUID(self._data["id"])

    @property
    def name(self) -> str:
        return self._data["name"]

    @property
    def status(self) -> str:
        self.refresh()
        return self._data["status"]

    def refresh(self):
        """Fetches the latest workflow data from the manager."""
        self._data = self._client._get(f"/api/v1/workflows/{self.id}")

    def execute(self) -> 'RemoteWorkflow':
        """Starts execution of the workflow."""
        self._data = self._client._post(f"/api/v1/workflows/{self.id}/execute")
        return self

    def pause(self) -> 'RemoteWorkflow':
        """Pauses the workflow."""
        self._data = self._client._post(f"/api/v1/workflows/{self.id}/pause")
        return self

    def resume(self) -> 'RemoteWorkflow':
        """Resumes the workflow."""
        self._data = self._client._post(f"/api/v1/workflows/{self.id}/resume")
        return self

    def reset(self) -> 'RemoteWorkflow':
        """Resets the workflow to DRAFT status."""
        self._data = self._client._post(f"/api/v1/workflows/{self.id}/reset")
        return self

    def delete(self):
        """Deletes the workflow."""
        self._client._delete(f"/api/v1/workflows/{self.id}")

    def clear_xcom(self):
        """Clears XCom history for this workflow."""
        self._client._delete(f"/api/v1/workflows/{self.id}/xcom")

    def get_xcom(self, key: str) -> Any:
        """[PLACEHOLDER] Retrieves a value from XCom for this workflow."""
        # TODO: Implement in next issue (XCom Management)
        return None

    def get_dag_graph(self) -> Dict[str, Any]:
        """Returns the full DAG graph (nodes and edges)."""
        return self._client._get(f"/api/v1/workflows/{self.id}/dag")

    def list_jobs(self, page: int = 0, size: int = 100) -> List[RemoteJob]:
        """Lists all jobs in this workflow using the paginated endpoint."""
        data = self._client._get(f"/api/v1/workflows/{self.id}/jobs", params={"page": page, "size": size})
        content = data.get("content", []) if isinstance(data, dict) else []
        return [RemoteJob(self._client, self.id, j) for j in content]

    def get_job(self, label: str) -> Optional[RemoteJob]:
        """Retrieves a specific job by its label using the specific workflow-job endpoint."""
        try:
            data = self._client._get(f"/api/v1/workflows/{self.id}/jobs/{label}")
            return RemoteJob(self._client, self.id, data)
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 404:
                return None
            raise

    def wait_for_completion(self, timeout_sec: int = 3600, poll_interval: int = 5) -> 'RemoteWorkflow':
        """Blocks until the workflow reaches a terminal state (COMPLETED, FAILED, CANCELLED)."""
        start_time = time.time()
        terminal_states = {"COMPLETED", "FAILED", "CANCELLED"}
        
        while time.time() - start_time < timeout_sec:
            current_status = self.status
            if current_status in terminal_states:
                return self
            time.sleep(poll_interval)
            
        raise TimeoutError(f"Workflow {self.id} did not complete within {timeout_sec} seconds.")

class FernOSClient:
    """Client for interacting with the Fern-OS Manager API."""
    def __init__(self, base_url: Optional[str] = None):
        if not base_url:
            host = os.environ.get("FERNOS_MANAGER_HOST", "localhost")
            port = os.environ.get("FERNOS_MANAGER_HTTP_PORT", "8080")
            base_url = f"http://{host}:{port}"
            
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()


    def _request(self, method, path, params=None, json=None, is_json=True):
        url = f"{self.base_url}{path}"
        response = self.session.request(method, url, params=params, json=json)
        response.raise_for_status()
        if is_json and response.text:
            return response.json()
        return response.text

    def _get(self, path, params=None, is_json=True):
        return self._request("GET", path, params=params, is_json=is_json)

    def _post(self, path, json=None):
        return self._request("POST", path, json=json)

    def _put(self, path, json=None):
        return self._request("PUT", path, json=json)

    def _delete(self, path):
        return self._request("DELETE", path, is_json=False)

    def register_dag(self, dag: 'DAG', execute: bool = True) -> RemoteWorkflow:
        """Registers a DAG and optionally starts execution."""
        data = self._post("/api/v1/workflows", json=dag.to_dict())
        workflow = RemoteWorkflow(self, data)
        if execute:
            workflow.execute()
        return workflow

    def list_workflows(self, page: int = 0, size: int = 20, status: Optional[str] = None) -> List[RemoteWorkflow]:
        """Lists workflows."""
        params: Dict[str, Any] = {"page": page, "size": size}
        if status: params["status"] = status
        data = self._get("/api/v1/workflows", params=params)
        # Spring Page response usually has 'content'
        content = data.get("content", []) if isinstance(data, dict) else []
        return [RemoteWorkflow(self, w) for w in content]

    def get_workflow(self, workflow_id: UUID) -> RemoteWorkflow:
        """Retrieves a workflow by ID."""
        data = self._get(f"/api/v1/workflows/{workflow_id}")
        return RemoteWorkflow(self, data)

    def submit_job(self, job: 'Job') -> RemoteJob:
        """Submits an ad-hoc job."""
        data = self._post("/api/v1/jobs", json=job.to_dict())
        # Note: We need a workflow context or a way to handle ad-hoc jobs without a workflow.
        # Ad-hoc jobs in the manager might still belong to a ghost workflow or have a different structure.
        # Assuming JobResponse has a workflowId.
        return RemoteJob(self, UUID(data.get("workflowId", "00000000-0000-0000-0000-000000000000")), data)

    def list_workers(self) -> List[Dict[str, Any]]:
        """Lists registered workers."""
        return self._get("/api/v1/workers")

    def get_worker(self, worker_id: UUID) -> Dict[str, Any]:
        """Retrieves details for a specific worker."""
        return self._get(f"/api/v1/workers/{worker_id}")
