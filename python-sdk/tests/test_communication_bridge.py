import unittest
import threading
import time
import json
import io
import sys
from unittest.mock import patch, MagicMock
from fernos.worker.bridge import CommunicationBridge
from fernos.worker.xcom import XCom

class MockStdio:
    def __init__(self):
        self.stdout = io.StringIO()
        self.lock = threading.Lock()
        
    def write(self, s):
        with self.lock:
            self.stdout.write(s)
            
    def flush(self):
        pass
        
    def getvalue(self):
        return self.stdout.getvalue()

class TestCommunicationBridge(unittest.TestCase):

    def test_multithreaded_xcom(self):
        """
        Tests that multiple threads can use XCom simultaneously with mocked stdio.
        """
        xcom = XCom()
        mock_stdio = MockStdio()
        
        # We need a custom readline that can handle concurrent requests
        # In a real scenario, the worker sends ACKs.
        # Here we mock stdin.readline to return "ACK" for every call.
        responses = ["ACK\n"] * 100
        mock_stdin = MagicMock()
        mock_stdin.readline.side_effect = responses

        def worker_thread(thread_id):
            for i in range(10):
                xcom.push(f"key_{thread_id}_{i}", f"val_{thread_id}_{i}")
                time.sleep(0.01)

        with patch('sys.stdout', mock_stdio), patch('sys.stdin', mock_stdin):
            threads = []
            for i in range(5):
                t = threading.Thread(target=worker_thread, args=(i,))
                threads.append(t)
                t.start()
                
            for t in threads:
                t.join()

        # Verify that we got 50 push commands
        output = mock_stdio.getvalue()
        lines = [l for l in output.split('\n') if l.strip()]
        self.assertEqual(len(lines), 50)
        for line in lines:
            self.assertTrue(line.startswith("__FERN_CMD__Xcom:"))
            payload = json.loads(line.replace("__FERN_CMD__Xcom:", ""))
            self.assertEqual(payload["command"], "push")

    def test_log_xcom_interleaving(self):
        """
        Tests that logs and XCom commands don't interleave even when called concurrently.
        """
        xcom = XCom()
        mock_stdio = MockStdio()
        mock_stdin = MagicMock()
        mock_stdin.readline.return_value = "ACK\n"

        def log_thread():
            for i in range(50):
                CommunicationBridge.log(f"LOG MESSAGE {i}")
        
        def xcom_thread():
            for i in range(50):
                xcom.push("sync_key", i)

        with patch('sys.stdout', mock_stdio), patch('sys.stdin', mock_stdin):
            t1 = threading.Thread(target=log_thread)
            t2 = threading.Thread(target=xcom_thread)
            t1.start()
            t2.start()
            t1.join()
            t2.join()

        output = mock_stdio.getvalue()
        lines = [l for l in output.split('\n') if l.strip()]
        self.assertEqual(len(lines), 100)
        
        log_count = sum(1 for l in lines if l.startswith("LOG MESSAGE"))
        xcom_count = sum(1 for l in lines if l.startswith("__FERN_CMD__Xcom:"))
        
        self.assertEqual(log_count, 50)
        self.assertEqual(xcom_count, 50)
        # If they interleaved, some lines wouldn't match either prefix or would have corrupted JSON

if __name__ == '__main__':
    unittest.main()
