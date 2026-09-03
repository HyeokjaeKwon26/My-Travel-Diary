#!/usr/bin/env python3
"""
tools/package_handoff.py

Packages the Traveler Android project into a clean, distributable ZIP handoff.
Enforces strict exclusion of:
- local.properties (must NEVER leak into handoffs)
- .git, .gradle, .idea, build/, */build/
- .DS_Store, *.iml, *.hprof, *.pyc, __pycache__

Ensures inclusion of:
- local.properties.example
- All source files, assets, schemas, tests, tools, docs, and build scripts.
"""

import os
import sys
import zipfile
import hashlib

EXCLUDE_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    ".tempmediaStorage",
    "__pycache__",
    ".cxx"
}

EXCLUDE_FILES = {
    "local.properties",
    ".DS_Store",
    "Thumbs.db"
}

EXCLUDE_EXTENSIONS = {
    ".iml",
    ".hprof",
    ".pyc"
}

def is_excluded(rel_path: str) -> bool:
    parts = rel_path.replace("\\", "/").split("/")
    for part in parts:
        if part in EXCLUDE_DIRS:
            return True
    
    filename = parts[-1]
    if filename in EXCLUDE_FILES:
        return True
        
    _, ext = os.path.splitext(filename)
    if ext in EXCLUDE_EXTENSIONS:
        return True
        
    return False

def package_project(repo_root: str, output_zip_path: str):
    repo_root = os.path.abspath(repo_root)
    output_zip_path = os.path.abspath(output_zip_path)
    
    print(f"Packaging Traveler from: {repo_root}")
    print(f"Output ZIP target: {output_zip_path}")
    
    file_count = 0
    with zipfile.ZipFile(output_zip_path, "w", zipfile.ZIP_DEFLATED) as zip_out:
        for root, dirs, files in os.walk(repo_root):
            # Modify dirs in-place to skip excluded directories
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, repo_root)
                
                if full_path == output_zip_path or is_excluded(rel_path):
                    continue
                
                zip_out.write(full_path, rel_path)
                file_count += 1
                
    # Integrity check on generated zip
    has_local_properties = False
    has_local_properties_example = False
    
    with zipfile.ZipFile(output_zip_path, "r") as zip_check:
        for name in zip_check.namelist():
            if name == "local.properties" or name.endswith("/local.properties"):
                has_local_properties = True
            if name == "local.properties.example" or name.endswith("/local.properties.example"):
                has_local_properties_example = True
                
    if has_local_properties:
        print("ERROR: Generated ZIP contains forbidden local.properties file!", file=sys.stderr)
        os.remove(output_zip_path)
        sys.exit(1)
        
    if not has_local_properties_example:
        print("WARNING: local.properties.example was not found in generated ZIP.", file=sys.stderr)

    zip_size_bytes = os.path.getsize(output_zip_path)
    sha256 = hashlib.sha256()
    with open(output_zip_path, "rb") as f:
        while chunk := f.read(65536):
            sha256.update(chunk)
            
    print(f"Successfully packaged {file_count} files into: {output_zip_path}")
    print(f"Archive Size: {zip_size_bytes / (1024 * 1024):.2f} MB ({zip_size_bytes} bytes)")
    print(f"SHA-256: {sha256.hexdigest()}")

if __name__ == "__main__":
    repo_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    target_zip = os.path.join(repo_dir, "Traveler_Beta_Handoff.zip")
    if len(sys.argv) > 1:
        target_zip = sys.argv[1]
    package_project(repo_dir, target_zip)
