import ast
import os
import base64
import sys
from typing import Dict, Set, List, Optional

class ScriptPreprocessor:
    """
    Handles module injection for Fern-OS Python jobs.
    Scans for # @fernos_include directives followed by imports and bundles local dependencies.
    """
    
    DIRECTIVE = "@fernos_include"
    
    def __init__(self, base_path: str):
        self.base_path = base_path
        self.resolved_modules: Dict[str, str] = {} # name -> source (Insertion order matters!)
        self.seen_files: Set[str] = set()
        self.in_progress: Set[str] = set()

    def process(self, content: str) -> str:
        """Processes the script content and injects the preamble."""
        try:
            tree = ast.parse(content)
        except SyntaxError:
            return content

        lines = content.splitlines()
        directive_line_numbers = [i + 1 for i, line in enumerate(lines) if self.DIRECTIVE in line]
        
        if not directive_line_numbers:
            return content
            
        modules_to_include = []
        
        # Find imports that follow a directive line
        for node in ast.walk(tree):
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                for d_lineno in directive_line_numbers:
                    #NOTE: we will allow imports to be as far as 3 lines after the directive
                    if 0 < node.lineno - d_lineno <= 3: 
                        if isinstance(node, ast.Import):
                            for alias in node.names:
                                modules_to_include.append(alias.name)
                        elif isinstance(node, ast.ImportFrom):
                            if node.module:
                                modules_to_include.append(node.module)
                        break

        if not modules_to_include:
            return content

        for mod_name in modules_to_include:
            self._resolve_module_recursive(mod_name)

        if not self.resolved_modules:
            return content

        preamble = self._generate_preamble()
        return preamble + "\n" + content

    def _resolve_module_recursive(self, mod_name: str):
        """Resolves a module and its dependencies in reverse topological order."""
        if mod_name in self.resolved_modules or mod_name in sys.builtin_module_names:
            return
            
        if mod_name in self.in_progress:
            return

        file_path = self._find_module_path(mod_name)
        if not file_path:
            return

        abs_path = os.path.abspath(file_path)
        if abs_path in self.seen_files:
            # Already bundled under a different name? Unlikely but safe.
            return
            
        self.in_progress.add(mod_name)
        try:
            with open(abs_path, 'r') as f:
                source = f.read()
            
            # Recursively resolve parents
            if '.' in mod_name:
                parent_name = '.'.join(mod_name.split('.')[:-1])
                self._resolve_module_recursive(parent_name)

            # Auto-discover dependencies within bundled modules
            tree = ast.parse(source)
            for node in ast.walk(tree):
                if isinstance(node, (ast.Import, ast.ImportFrom)):
                    if isinstance(node, ast.Import):
                        for alias in node.names:
                            self._resolve_module_recursive(alias.name)
                    elif isinstance(node, ast.ImportFrom):
                        if node.module:
                            self._resolve_module_recursive(node.module)
            
            # Add to resolved_modules ONLY AFTER all dependencies are resolved
            # This ensures reverse topological order (bottom-up)
            self.resolved_modules[mod_name] = source
            self.seen_files.add(abs_path)
        except Exception:
            pass
        finally:
            self.in_progress.discard(mod_name)

    def _find_module_path(self, mod_name: str) -> Optional[str]:
        """Translates 'a.b.c' to 'a/b/c.py' or 'a/b/c/__init__.py' relative to base_path."""
        parts = mod_name.split('.')
        base = os.path.join(self.base_path, *parts)
        if os.path.isfile(base + ".py"):
            return base + ".py"
        if os.path.isdir(base) and os.path.isfile(os.path.join(base, "__init__.py")):
            return os.path.join(base, "__init__.py")
        return None

    def _generate_preamble(self) -> str:
        """Generates the preamble code."""
        modules_data = {}
        # Preserving insertion order (resolved_modules keys are in bottom-up order)
        for name, source in self.resolved_modules.items():
            modules_data[name] = base64.b64encode(source.encode()).decode()

        return f"""
# --- FERN-OS MODULE INJECTION ---
def _fernos_inject():
    import sys, types, base64
    mapping = {modules_data}
    for name, b64_source in mapping.items():
        if name in sys.modules: continue
        parts = name.split('.')
        for i in range(1, len(parts)):
            p = '.'.join(parts[:i])
            if p not in sys.modules:
                sys.modules[p] = types.ModuleType(p)
                if i > 1:
                    setattr(sys.modules['.'.join(parts[:i-1])], parts[i-1], sys.modules[p])
        m = types.ModuleType(name)
        m.__file__ = f"<fernos>/{{name.replace('.', '/')}}.py"
        try:
            exec(base64.b64decode(b64_source), m.__dict__)
            sys.modules[name] = m
            if '.' in name:
                p_name, c_name = name.rsplit('.', 1)
                setattr(sys.modules[p_name], c_name, m)
        except Exception as e:
            sys.stderr.write(f"Fern-OS Injection Error [{{name}}]: {{e}}\\n")
_fernos_inject()
del _fernos_inject
# --- END FERN-OS MODULE INJECTION ---
"""
