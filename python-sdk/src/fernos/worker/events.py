import os
import socket
import struct
import json
import threading
import logging
from typing import Dict, Any, Callable, Set, Optional
from uuid import UUID

LOG = logging.getLogger(__name__)

class MessageType:
    REGISTER_WORKER = 0x01
    HEARTBEAT = 0x02
    ASSIGN_JOB = 0x03
    JOB_RESULT = 0x04
    REGISTER_ACK = 0x05
    JOB_RUNNING = 0x06
    CANCEL_JOB = 0x07
    XCOM_REQUEST = 0x08
    XCOM_RESPONSE = 0x09
    JOB_LOG = 0x0A
    REGISTER_JOB_EVENTS = 0x0B
    EVENT_REGISTER = 0x0C
    EVENT_SUBSCRIBE = 0x0D
    EVENT_PUBLISH = 0x0E
    EVENT_NOTIFY = 0x0F

class EventsClient:
    """
    Background client for handling real-time events via a dedicated TCP connection to the Manager.
    """
    def __init__(self, manager_host: str, manager_port: int, job_token: str):
        self.host = manager_host
        self.port = manager_port
        self.token = job_token
        self.socket: Optional[socket.socket] = None
        self._handlers: Dict[str, Set[Callable[[Dict[str, Any]], None]]] = {}
        self._running = False
        self._listener_thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()

    def connect(self):
        """Establishes connection and registers for events."""
        with self._lock:
            if self._running:
                return

            LOG.info(f"Connecting to Manager events at {self.host}:{self.port}")
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # Initial registration
            payload = {"auth_token": self.token}
            self._send(MessageType.REGISTER_JOB_EVENTS, payload)
            
            # Wait for ACK
            msg_type, ack_payload = self._receive()
            if msg_type != MessageType.REGISTER_ACK:
                raise ConnectionError(f"Expected REGISTER_ACK, got 0x{msg_type:02X}")
            
            LOG.info(f"Registered for events. Job ID: {ack_payload.get('jobId')}")

            self._running = True
            self._listener_thread = threading.Thread(target=self._listen_loop, daemon=True)
            self._listener_thread.start()

    def register_event(self, event_name: str):
        """Claim ownership of an event name."""
        self._send(MessageType.EVENT_REGISTER, {"eventName": event_name})

    def subscribe(self, event_name: str, callback: Callable[[Dict[str, Any]], None]):
        """Subscribe to an event and provide a callback."""
        with self._lock:
            if event_name not in self._handlers:
                self._handlers[event_name] = set()
                # Send subscription to manager
                self._send(MessageType.EVENT_SUBSCRIBE, {"eventName": event_name})
            self._handlers[event_name].add(callback)

    def publish(self, event_name: str, data: Dict[str, Any]):
        """Publish an event."""
        payload = {
            "eventName": event_name,
            "data": data
        }
        self._send(MessageType.EVENT_PUBLISH, payload)

    def _send(self, msg_type: int, payload: Dict[str, Any]):
        """Encodes and sends a message."""
        payload_json = json.dumps(payload).encode('utf-8')
        header = struct.pack(">BI", msg_type, len(payload_json))
        self.socket.sendall(header + payload_json)

    def _receive(self) -> tuple[int, Dict[str, Any]]:
        """Blocking read of a single message."""
        header = self._read_exactly(5)
        msg_type, length = struct.unpack(">BI", header)
        payload_bytes = self._read_exactly(length)
        return msg_type, json.loads(payload_bytes.decode('utf-8'))

    def _read_exactly(self, n: int) -> bytes:
        data = b''
        while len(data) < n:
            chunk = self.socket.recv(n - len(data))
            if not chunk:
                raise EOFError("Socket closed")
            data += chunk
        return data

    def _listen_loop(self):
        """Background thread loop to handle EVENT_NOTIFY messages."""
        while self._running:
            try:
                msg_type, payload = self._receive()
                if msg_type == MessageType.EVENT_NOTIFY:
                    event_name = payload.get("eventName")
                    event_data = payload.get("data", {})
                    
                    with self._lock:
                        handlers = self._handlers.get(event_name, set()).copy()
                    
                    for handler in handlers:
                        try:
                            # Execute sequential as per user request "one at a time"
                            handler(event_data)
                        except Exception as e:
                            LOG.error(f"Error in event handler for '{event_name}': {e}")
                else:
                    LOG.debug(f"Received non-notify message on events channel: 0x{msg_type:02X}")
            except (EOFError, ConnectionResetError):
                LOG.warning("Events connection lost")
                break
            except Exception as e:
                LOG.error(f"Unexpected error in events listener: {e}")
                break
        self._running = False

class Events:
    """
    High-level Events API for Fern-OS Python jobs.
    """
    _instance: Optional['Events'] = None
    _lock = threading.Lock()

    def __init__(self):
        self.client: Optional[EventsClient] = None
        self._init_client()

    def _init_client(self):
        host = os.environ.get("FERNOS_MANAGER_HOST", "localhost")
        port = int(os.environ.get("FERNOS_MANAGER_TCP_PORT", "9090"))
        token = os.environ.get("FERNOS_JOB_TOKEN")

        
        if token:
            self.client = EventsClient(host, port, token)
            try:
                self.client.connect()
            except Exception as e:
                LOG.error(f"Failed to connect to events system: {e}")

    def register(self, event_name: str):
        if self.client:
            self.client.register_event(event_name)

    def on(self, event_name: str, callback: Callable[[Dict[str, Any]], None]):
        if self.client:
            self.client.subscribe(event_name, callback)

    def emit(self, event_name: str, data: Dict[str, Any]):
        if self.client:
            self.client.publish(event_name, data)

# Global instances for easy import
events = Events()
