"""
Python executor for LastChat Python Workbench.
Provides safe code execution with stdout capture and error handling.
"""

import sys
import os
import json
from io import StringIO


def execute(code: str, working_dir: str) -> str:
    """
    Execute Python code with stdout/stderr capture.
    
    Args:
        code: Python code to execute
        working_dir: Working directory for file operations
    
    Returns:
        JSON string with result/stdout/error
    """
    os.chdir(working_dir)
    
    # Configure matplotlib for non-GUI environment before any imports
    # This prevents black/empty images
    import matplotlib
    matplotlib.use('Agg')  # Use non-interactive backend
    import matplotlib.pyplot as plt
    plt.rcParams['figure.facecolor'] = 'white'  # White background instead of transparent
    plt.rcParams['axes.facecolor'] = 'white'
    plt.rcParams['savefig.facecolor'] = 'white'
    
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = StringIO()
    sys.stderr = StringIO()
    
    result = None
    error = None
    
    # Pre-populate globals with useful imports and matplotlib configured
    exec_globals = {
        '__name__': '__main__',
        '__builtins__': __builtins__,
        'plt': plt,
        'matplotlib': matplotlib,
    }
    
    try:
        # Try to evaluate as expression first (returns value)
        result = eval(code, exec_globals)
    except SyntaxError:
        # Not an expression, execute as statements
        try:
            exec(code, exec_globals)
            # Auto-save any open matplotlib figures
            for i, fig_num in enumerate(plt.get_fignums()):
                fig = plt.figure(fig_num)
                filename = f"figure_{i + 1}.png" if len(plt.get_fignums()) > 1 else "figure.png"
                fig.savefig(filename, dpi=150, bbox_inches='tight', facecolor='white', edgecolor='none')
                plt.close(fig)
        except Exception as e:
            error = f"{type(e).__name__}: {str(e)}"
    except Exception as e:
        error = f"{type(e).__name__}: {str(e)}"
    finally:
        stdout_output = sys.stdout.getvalue()
        stderr_output = sys.stderr.getvalue()
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        # Clean up any remaining figures
        plt.close('all')
    
    response = {}
    if error:
        response["error"] = error
    elif result is not None:
        response["result"] = str(result)
    
    if stdout_output:
        response["stdout"] = stdout_output
    if stderr_output:
        response["stderr"] = stderr_output
    
    if not response:
        response["result"] = "Executed successfully (no output)"
    
    return json.dumps(response)


def read_file(filepath: str, working_dir: str) -> str:
    """
    Read a file from the sandbox.
    
    Args:
        filepath: Relative or absolute path to file
        working_dir: Working directory for relative paths
    
    Returns:
        JSON string with content or error
    """
    try:
        if not os.path.isabs(filepath):
            filepath = os.path.join(working_dir, filepath)
        
        # Security: ensure file is within working_dir
        real_path = os.path.realpath(filepath)
        real_working = os.path.realpath(working_dir)
        if not real_path.startswith(real_working):
            return json.dumps({"error": "Access denied: path outside sandbox"})
        
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        return json.dumps({"content": content})
    except Exception as e:
        return json.dumps({"error": str(e)})


def write_file(filepath: str, content: str, working_dir: str) -> str:
    """
    Write content to a file in the sandbox.
    
    Args:
        filepath: Relative or absolute path
        content: Content to write
        working_dir: Working directory for relative paths
    
    Returns:
        JSON string with path or error
    """
    try:
        if not os.path.isabs(filepath):
            filepath = os.path.join(working_dir, filepath)
        
        # Security: ensure file is within working_dir
        real_path = os.path.realpath(filepath)
        real_working = os.path.realpath(working_dir)
        if not real_path.startswith(real_working):
            return json.dumps({"error": "Access denied: path outside sandbox"})
        
        # Create parent directories if needed
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return json.dumps({"path": filepath, "success": True})
    except Exception as e:
        return json.dumps({"error": str(e)})


def write_binary_file(filepath: str, data: bytes, working_dir: str) -> str:
    """
    Write binary data to a file in the sandbox.
    
    Args:
        filepath: Relative or absolute path
        data: Binary data to write
        working_dir: Working directory for relative paths
    
    Returns:
        JSON string with path or error
    """
    try:
        if not os.path.isabs(filepath):
            filepath = os.path.join(working_dir, filepath)
        
        # Security: ensure file is within working_dir
        real_path = os.path.realpath(filepath)
        real_working = os.path.realpath(working_dir)
        if not real_path.startswith(real_working):
            return json.dumps({"error": "Access denied: path outside sandbox"})
        
        # Create parent directories if needed
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        
        with open(filepath, 'wb') as f:
            f.write(data)
        return json.dumps({"path": filepath, "success": True})
    except Exception as e:
        return json.dumps({"error": str(e)})


def list_files(working_dir: str) -> str:
    """
    List files in the sandbox directory.
    
    Args:
        working_dir: Directory to list
    
    Returns:
        JSON string with file list
    """
    try:
        files = []
        for root, dirs, filenames in os.walk(working_dir):
            for filename in filenames:
                full_path = os.path.join(root, filename)
                rel_path = os.path.relpath(full_path, working_dir)
                size = os.path.getsize(full_path)
                files.append({"name": rel_path, "size": size})
        return json.dumps({"files": files})
    except Exception as e:
        return json.dumps({"error": str(e)})
