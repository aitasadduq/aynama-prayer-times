#!/usr/bin/env python3
"""
Validate every prayer-times test vector against test-vectors/schema.json.

schema.json has always described itself as "enforced by vectors.yml CI on every commit"; no such
workflow existed, so nothing checked it. This is that check, wired into ios.yml.

It is not redundant with VectorParityTests. That suite iterates the keys a case happens to carry,
so a regenerated vector that dropped `isha` would compare six times, pass, and quietly stop
guarding the seventh. The schema's `required` list is what says all seven must be there.

Run:  python3 scripts/validate-vectors.py
Needs: jsonschema.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from jsonschema import Draft7Validator

REPO_ROOT = Path(__file__).parent.parent
SCHEMA = REPO_ROOT / "test-vectors" / "schema.json"
VECTORS = REPO_ROOT / "test-vectors" / "prayer-times"


def main() -> int:
    validator = Draft7Validator(json.loads(SCHEMA.read_text()))
    files = sorted(VECTORS.glob("*.json"))
    if not files:
        print(f"::error::no vector files under {VECTORS.relative_to(REPO_ROOT)}", file=sys.stderr)
        return 1

    failures = 0
    for path in files:
        relative = path.relative_to(REPO_ROOT)
        try:
            document = json.loads(path.read_text())
        except json.JSONDecodeError as error:
            print(f"::error file={relative}::not valid JSON: {error}", file=sys.stderr)
            failures += 1
            continue
        errors = sorted(validator.iter_errors(document), key=lambda e: list(e.path))
        for error in errors:
            where = "/".join(str(part) for part in error.path) or "<root>"
            print(f"::error file={relative}::{where}: {error.message}", file=sys.stderr)
        failures += len(errors)
        if not errors:
            print(f"[ok] {relative} — {len(document['cases'])} case(s)")

    if failures:
        print(f"[error] {failures} schema violation(s)", file=sys.stderr)
        return 1
    print(f"[ok] {len(files)} vector file(s) valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
