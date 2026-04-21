from fernos.storage import FernMiniIO

def get_storage():
    """Shared utility to initialize storage."""
    print ("[Common Utils] Initializing storage client...")
    return FernMiniIO()
