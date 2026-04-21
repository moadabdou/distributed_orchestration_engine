import sys
import threading
import json
import logging
from typing import Any, Dict, Optional

LOG = logging.getLogger(__name__)

class CommunicationBridge:
    """
    Centralized, thread-safe bridge for communication between the Python SDK
    and the Java Worker via stdin/stdout.
    """
    _lock = threading.Lock()
    XCOM_PREFIX = "__FERN_CMD__Xcom:"
    LOG_PREFIX = "__FERN_CMD__LOG:"

    @classmethod
    def write_log(cls, message: str):
        """
        Thread-safe logging to stdout with fernos prefix.
        """
        with cls._lock:
            sys.stdout.write(f"{cls.LOG_PREFIX}{message}\n")
            sys.stdout.flush()

    @classmethod
    def request(cls, cmd_payload: Dict[str, Any]) -> str:
        """
        Thread-safe request-response cycle.
        Writes an XCom command to stdout and waits for the immediate response on stdin.
        """
        with cls._lock:
            # Atomic write + read to ensure this thread gets the matching response
            payload_json = json.dumps(cmd_payload)
            sys.stdout.write(f"{cls.XCOM_PREFIX}{payload_json}\n")
            sys.stdout.flush()
            
            response = sys.stdin.readline()
            if not response:
                raise EOFError("Read empty response from worker (stdin closed?)")
            return response.strip()

# Convenience instance if needed, though class methods are fine
bridge = CommunicationBridge()
