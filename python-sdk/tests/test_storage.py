import unittest
from unittest.mock import patch
import os
from fernos.storage import FernMiniIO

class TestStorage(unittest.TestCase):

    @patch.dict(os.environ, {
        "MINIO_ENDPOINT": "http://localhost:9000",
        "MINIO_ACCESS_KEY": "admin",
        "MINIO_SECRET_KEY": "password123",
        "MINIO_BUCKET": "test-bucket"
    })
    def test_storage_init_success(self):
        storage = FernMiniIO()
        self.assertEqual(storage.endpoint, "http://localhost:9000")
        self.assertEqual(storage.bucket, "test-bucket")
        self.assertIsNotNone(storage.client)

    @patch.dict(os.environ, {}, clear=True)
    def test_storage_init_failure(self):
        with self.assertRaises(RuntimeError) as cm:
            FernMiniIO()
        self.assertIn("Missing MinIO configuration", str(cm.exception))

    @patch.dict(os.environ, {
        "MINIO_ENDPOINT": "https://s3.amazonaws.com",
        "MINIO_ACCESS_KEY": "admin",
        "MINIO_SECRET_KEY": "password123",
    })
    def test_storage_secure_endpoint(self):
        storage = FernMiniIO()
        # Minio client secure parameter should be True for https
        self.assertTrue(storage.client._base_url.is_https)

if __name__ == '__main__':
    unittest.main()
