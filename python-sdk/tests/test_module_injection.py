import os
import sys
import subprocess
import pytest
from fernos import PythonJob, DAG

import fernos
print(f"DEBUG: fernos file: {fernos.__file__}")

def test_natural_import_injection(tmp_path):
    # Create a local module structure
    lib_dir = tmp_path / "mylib"
    lib_dir.mkdir()
    (lib_dir / "__init__.py").write_text("X = 10")
    (lib_dir / "utils.py").write_text("def greet(name): return f'Hello {name}'")
    
    main_script = """
# @fernos_include
from mylib.utils import greet
# @fernos_include
import mylib

print(greet('Fern'))
print(mylib.X)
"""
    main_path = tmp_path / "main.py"
    main_path.write_text(main_script)
    
    # We must be in the tmp_path for resolution to work if it uses CWD, 
    # but PythonJob uses dirname(script_path) as base_path.
    with DAG("test-injection"):
        job = PythonJob("test", script_path=str(main_path))
        
    content = job.script_content
    
    # Check if preamble exists
    assert "# --- FERN-OS MODULE INJECTION ---" in content
    assert "mylib.utils" in content
    assert "mylib" in content
    
    # Verify execution (requires self-contained preamble)
    result = subprocess.run([sys.executable, "-c", content], capture_output=True, text=True)
    assert result.returncode == 0
    assert "Hello Fern" in result.stdout.strip()
    assert "10" in result.stdout.strip()

def test_recursive_module_injection(tmp_path):
    # A -> B -> C
    (tmp_path / "c.py").write_text("VAL = 'C'")
    # Note: the preprocessor will auto-discover 'import c' inside b.py 
    # because it's bundled.
    (tmp_path / "b.py").write_text("import c\ndef get_val(): return c.VAL")
    (tmp_path / "a.py").write_text("import b\ndef run(): return b.get_val()")
    
    main_script = """
# @fernos_include
import a
print(a.run())
"""
    main_path = tmp_path / "main.py"
    main_path.write_text(main_script)
    
    with DAG("test-recursive"):
        job = PythonJob("test", script_path=str(main_path))
        
    result = subprocess.run([sys.executable, "-c", job.script_content], capture_output=True, text=True)
    assert result.returncode == 0
    assert "C" in result.stdout.strip()

def test_no_injection_if_no_directive(tmp_path):
    (tmp_path / "lib.py").write_text("X = 1")
    main_script = "import lib\nprint(lib.X)"
    main_path = tmp_path / "main.py"
    main_path.write_text(main_script)
    
    with DAG("test-none"):
        job = PythonJob("test", script_path=str(main_path))
        
    assert "# --- FERN-OS MODULE INJECTION ---" not in job.script_content

def test_syntax_error_handling(tmp_path):
    main_script = "# @fernos_include\nimport logic\nif True print('oops')" # missing colon
    main_path = tmp_path / "main.py"
    main_path.write_text(main_script)
    
    # Should not crash, just return original content or failing to inject
    with DAG("test-error"):
        job = PythonJob("test", script_path=str(main_path))
    
    assert "if True print('oops')" in job.script_content
