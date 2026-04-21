import os
from minio import Minio

class FernMiniIO:
    """
    Wrapper around Minio client that automatically configures itself
    from Fern-OS environment variables.
    """
    
    def __init__(self):
        self.endpoint = os.environ.get("MINIO_ENDPOINT")
        self.access_key = os.environ.get("MINIO_ACCESS_KEY")
        self.secret_key = os.environ.get("MINIO_SECRET_KEY")
        self.bucket = os.environ.get("MINIO_BUCKET")
        
        if not all([self.endpoint, self.access_key, self.secret_key]):
            missing = [k for k, v in {
                "MINIO_ENDPOINT": self.endpoint,
                "MINIO_ACCESS_KEY": self.access_key,
                "MINIO_SECRET_KEY": self.secret_key
            }.items() if not v]
            raise RuntimeError(f"Missing MinIO configuration in environment variables: {', '.join(missing)}")
        
        # Remove http:// or https:// prefix for Minio client if present
        clean_endpoint = self.endpoint
        secure = False
        if clean_endpoint.startswith("http://"):
            clean_endpoint = clean_endpoint[7:]
            secure = False
        elif clean_endpoint.startswith("https://"):
            clean_endpoint = clean_endpoint[8:]
            secure = True
            
        self.client = Minio(
            clean_endpoint,
            access_key=self.access_key,
            secret_key=self.secret_key,
            secure=secure
        )

    def get_client(self) -> Minio:
        """
        Returns the underlying Minio client.
        """
        return self.client
    
    def get_bucket(self) -> str:
        """
        Returns the configured default bucket name.
        """
        return self.bucket
