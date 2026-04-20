from typing import List, Optional, Union, Dict, Any
import threading
import json

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
        
        # Register with the current DAG context if available
        current_dag = _get_current_dag()
        if current_dag:
            current_dag.add_job(self)

    def _resolve_script(self, script: Optional[str], path: Optional[str]) -> str:
        """Resolves script content from either raw string or file path."""
        if script:
            return script
        if path:
            try:
                import os
                # Try relative to caller's file or absolute
                with open(path, 'r') as f:
                    return f.read()
            except Exception as e:
                raise ValueError(f"Failed to read script from path '{path}': {e}")
        return ""

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

    def __rshift__(self, other: Union['Job', List['Job']]) -> Union['Job', List['Job']]:
        if isinstance(other, list):
            for job in other:
                job.upstream.add(self.label)
        else:
            other.upstream.add(self.label)
        return other

    def __lshift__(self, other: Union['Job', List['Job']]) -> Union['Job', List['Job']]:
        if isinstance(other, list):
            for job in other:
                self.upstream.add(job.label)
        else:
            self.upstream.add(other.label)
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
        for job in self.jobs:
            for up in job.upstream:
                dependencies.append({
                    "fromJobLabel": up,
                    "toJobLabel": job.label
                })
                
        return {
            "name": self.name,
            "jobs": jobs_data,
            "dependencies": dependencies
        }
