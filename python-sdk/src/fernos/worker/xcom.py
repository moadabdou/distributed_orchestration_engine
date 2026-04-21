import sys
import json
import logging

LOG = logging.getLogger(__name__)

class XCom:
    """
    XCom client for inter-job communication.
    Works by communicating with the Java worker via stdout/stdin.
    """
    
    CMD_PREFIX = "__FERN_CMD__Xcom:"

    def push(self, key: str, value: any, type: str = "message"):
        """
        Pushes a value to XCom.
        
        Args:
            key: The XCom key.
            value: The value to push (should be JSON serializable).
            type: The type of XCom (default: "message").
        """
        payload = {
            "command": "push",
            "key": key,
            "value": value,
            "type": type
        }
        
        # Write command to stdout
        sys.stdout.write(f"{self.CMD_PREFIX}{json.dumps(payload)}\n")
        sys.stdout.flush()
        
        # Wait for ACK on stdin
        ack = sys.stdin.readline()
        if ack.strip() != "ACK":
            raise RuntimeError(f"Failed to receive ACK from worker for XCom push. Got: {ack.strip()}")

    def pull(self, key: str) -> any:
        """
        Pulls a value from XCom.
        
        Args:
            key: The XCom key (usually the label of the upstream job).
            
        Returns:
            The XCom value, or None if not found.
        """
        payload = {
            "command": "pull",
            "key": key
        }
        
        # Write command to stdout
        sys.stdout.write(f"{self.CMD_PREFIX}{json.dumps(payload)}\n")
        sys.stdout.flush()
        
        # Wait for response on stdin
        response_json = sys.stdin.readline()
        if not response_json:
            raise RuntimeError("Failed to receive response from worker for XCom pull (EOF)")
            
        try:
            response = json.loads(response_json)
            if response.get("status") == "SUCCESS":
                return response.get("value")
            elif response.get("status") == "NOT_FOUND":
                return None
            else:
                raise RuntimeError(f"XCom pull failed with status: {response.get('status')}")
        except json.JSONDecodeError:
            raise RuntimeError(f"Failed to decode XCom pull response: {response_json}")

# For easy usage: xcom = XCom()
xcom = XCom()
