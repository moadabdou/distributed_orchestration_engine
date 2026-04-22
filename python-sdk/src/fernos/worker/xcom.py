import json
import logging
from .bridge import CommunicationBridge

LOG = logging.getLogger(__name__)

class XCom:
    """
    XCom client for inter-job communication.
    Works by communicating with the Java worker via stdout/stdin through a thread-safe bridge.
    """
    
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
        
        # Dispatch request via bridge
        ack = CommunicationBridge.request(payload)
        
        if ack != "ACK":
            raise RuntimeError(f"Failed to receive ACK from worker for XCom push. Got: {ack}")

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
        
        # Dispatch request via bridge
        response_json = CommunicationBridge.request(payload)
            
        try:
            response = json.loads(response_json)
            if response.get("status") == "SUCCESS":
                #try to deserialize the value
                try:
                    return json.loads(response.get("value"))
                except json.JSONDecodeError:
                    return response.get("value")
            elif response.get("status") == "NOT_FOUND":
                return None
            else:
                raise RuntimeError(f"XCom pull failed with status: {response.get('status')}")
        except json.JSONDecodeError:
            raise RuntimeError(f"Failed to decode XCom pull response: {response_json}")

# For easy usage: xcom = XCom()
xcom = XCom()
