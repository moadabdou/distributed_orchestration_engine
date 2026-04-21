# Issue #054: Script Preprocessor & Module Injection

## Description

Support local code reuse in job scripts through a custom preprocessor that handles module injection before execution. This allows scripts to remain small while leveraging shared utilities.

## Requirements

1.  Implement a preprocessor that scans scripts for the `# @fernos_include` directive.
2.  The directive should be followed by a path to a Python file (e.g., `import utils/text_cleaner.py`).
3.  The preprocessor must:
    - Locate the referenced file relative to the script or DAG base.
    - Inline the code or bundle it so the worker can resolve the import.
    - Ensure that the imported module is correctly namespaced (e.g., `text_cleaner.clean_text`).
4.  Handle recursive includes safely.

## Technical Details

- Location: `python-sdk/fernos_sdk/compiler/preprocessor.py`.
- This logic should run during the `fernos deploy` phase or when local execution is triggered.
- The manager/worker must be able to handle "fat" scripts or a bundle containing the dependencies.

## Acceptance Criteria

- [ ] Scripts can import local utilities via `# @fernos_include`
- [ ] Included files are correctly bundled and uploaded during deployment
- [ ] Job execution succeeds on the worker node with all dependencies resolved
