import unittest
from unittest.mock import patch, MagicMock
import sys
import io
import json
from fernos.worker.xcom import XCom

class TestXCom(unittest.TestCase):

    @patch('sys.stdout', new_callable=io.StringIO)
    @patch('sys.stdin', new_callable=io.StringIO)
    def test_xcom_push(self, mock_stdin, mock_stdout):
        mock_stdin.write("ACK\n")
        mock_stdin.seek(0)
        
        xcom = XCom()
        xcom.push("test_key", {"data": 123})
        
        output = mock_stdout.getvalue()
        self.assertIn("__FERN_CMD__Xcom:", output)
        payload = json.loads(output.replace("__FERN_CMD__Xcom:", "").strip())
        self.assertEqual(payload["command"], "push")
        self.assertEqual(payload["key"], "test_key")
        self.assertEqual(payload["value"], {"data": 123})

    @patch('sys.stdout', new_callable=io.StringIO)
    @patch('sys.stdin', new_callable=io.StringIO)
    def test_xcom_pull_success(self, mock_stdin, mock_stdout):
        response = {
            "status": "SUCCESS",
            "key": "upstream",
            "value": "secret_data"
        }
        mock_stdin.write(json.dumps(response) + "\n")
        mock_stdin.seek(0)
        
        xcom = XCom()
        val = xcom.pull("upstream")
        
        self.assertEqual(val, "secret_data")
        output = mock_stdout.getvalue()
        self.assertIn("__FERN_CMD__Xcom:", output)
        payload = json.loads(output.replace("__FERN_CMD__Xcom:", "").strip())
        self.assertEqual(payload["command"], "pull")
        self.assertEqual(payload["key"], "upstream")

    @patch('sys.stdout', new_callable=io.StringIO)
    @patch('sys.stdin', new_callable=io.StringIO)
    def test_xcom_pull_not_found(self, mock_stdin, mock_stdout):
        response = {
            "status": "NOT_FOUND",
            "key": "upstream",
            "value": None
        }
        mock_stdin.write(json.dumps(response) + "\n")
        mock_stdin.seek(0)
        
        xcom = XCom()
        val = xcom.pull("upstream")
        
        self.assertIsNone(val)

if __name__ == '__main__':
    unittest.main()
