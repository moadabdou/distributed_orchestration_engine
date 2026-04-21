import os
import pytest
from fernos import PythonJob, DAG

def test_recursive_inclusion(tmp_path):
    # Create a structure:
    # main.py -> includes utils/helper.py
    # utils/helper.py -> includes utils/logger.py
    
    utils_dir = tmp_path / "utils"
    utils_dir.mkdir()
    
    logger_path = utils_dir / "logger.py"
    logger_path.write_text("def log(msg): print(f'LOG: {msg}')")
    
    helper_path = utils_dir / "helper.py"
    helper_path.write_text("""
# @fernos_include
import utils/logger.py

def help():
    log('helping')
""")
    # Note: in helper.py, it includes utils/logger.py but it's already in utils/.
    # Wait, my logic resolves relative to base_path (dirname of helper.py).
    # So if helper.py is in utils/, and it says 'import logger.py', it should work.
    # If it says 'import utils/logger.py', it would look for utils/utils/logger.py.
    
    # Let's fix the helper path to be relative or correct.
    helper_path.write_text("""
# @fernos_include
import logger.py

def help():
    log('helping')
""")

    main_path = tmp_path / "main.py"
    main_path.write_text("""
# @fernos_include
import utils/helper.py

help()
""")

    with DAG("test-inclusion"):
        job = PythonJob("test", script_path=str(main_path))
    
    expected_content = """
def log(msg): print(f'LOG: {msg}')

def help():
    log('helping')

help()
"""
    # re.sub might leave some whitespace/newlines depending on how it's matched.
    # Let's check the result.
    
    # Strip whitespaces for easier comparison if needed, or just be precise.
    assert "def log(msg)" in job.script_content
    assert "def help()" in job.script_content
    assert "help()" in job.script_content
    assert "# @fernos_include" not in job.script_content
    assert "import utils/helper.py" not in job.script_content

def test_circular_inclusion(tmp_path):
    a_path = tmp_path / "a.py"
    b_path = tmp_path / "b.py"
    
    a_path.write_text("""
# @fernos_include
import b.py
""")
    b_path.write_text("""
# @fernos_include
import a.py
""")
    
    with DAG("test-circular"):
        job = PythonJob("test", script_path=str(a_path))
    
    assert "Circular include detected: a.py" in job.script_content

def test_missing_inclusion(tmp_path):
    main_path = tmp_path / "main.py"
    main_path.write_text("""
# @fernos_include
import missing.py
""")
    
    with DAG("test-missing"):
        job = PythonJob("test", script_path=str(main_path))
        
    assert "Failed to include missing.py" in job.script_content
