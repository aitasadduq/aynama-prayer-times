#!/usr/bin/env python3
"""Keep the named app screenshots from xcresulttool's attachment export.

Xcode also exports large automatic diagnostics for every UI test. Those remain in
Aynama.xcresult; the small screenshot artifact contains only the intentional captures.
"""
import json
import re
import shutil
import sys
from pathlib import Path

source, destination = map(Path, sys.argv[1:])
destination.mkdir(parents=True, exist_ok=True)


def collect(value):
    if isinstance(value, list):
        for item in value:
            collect(item)
    elif isinstance(value, dict):
        filename = value.get("exportedFileName")
        if filename:
            for text in value.values():
                if not isinstance(text, str):
                    continue
                match = re.search(r"\b(\d{2}-(?:prayers|qibla|tracker|settings|profile|notifications|prayer)[a-z-]*)", text)
                if match:
                    path = source / filename
                    if path.is_file() and path.suffix.lower() in {".png", ".jpg", ".jpeg", ".heic"}:
                        shutil.copyfile(path, destination / (match.group(1) + path.suffix.lower()))
                        return
        for item in value.values():
            collect(item)


manifest = source / "manifest.json"
if manifest.exists():
    collect(json.loads(manifest.read_text()))
count = len(list(destination.glob("*")))
print(f"Collected {count} named screenshots")
