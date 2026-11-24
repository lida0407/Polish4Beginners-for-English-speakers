#!/usr/bin/env python3
"""Split a worker's reply ('=== FILE: path ===' markers) into files under docs/app.
    python3 web-build/split.py web-build/briefs/x.out [--only js/views]"""
import re
import sys
from pathlib import Path

APP = Path(__file__).resolve().parent.parent / "docs" / "app"
text = Path(sys.argv[1]).read_text(encoding="utf-8")
parts = re.split(r"^=== FILE: (.+?) ===\s*$", text, flags=re.M)
if len(parts) < 3:
    sys.exit("no FILE markers found")
for path, body in zip(parts[1::2], parts[2::2]):
    path = path.strip()
    if ".." in path or path.startswith("/") or not path.startswith("js/"):
        sys.exit(f"refusing path {path}")
    body = re.sub(r"^\s*```[a-z]*\n", "", body)
    body = re.sub(r"\n```\s*$", "\n", body.rstrip() + "\n")
    dest = APP / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(body, encoding="utf-8")
    print(f"{path}: {len(body.splitlines())} lines")
