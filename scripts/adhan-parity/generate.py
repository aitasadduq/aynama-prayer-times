#!/usr/bin/env python3
"""
Generate the prayer-times test vectors from Adhan-Kotlin, the reference implementation.

The vectors are what Android actually produces, because Phase 3A makes the tested Android
behaviour the product specification: iOS is held to Android's numbers, not to a third opinion.
(scripts/generate_vectors.py, when it lands, cross-validates those numbers against PrayTimes.py
before they are trusted — a different question from cross-*port* parity, which is this file's.)

Writes:
  test-vectors/prayer-times/<method>.json       one file per calculation method, schema.json shape
  scripts/adhan-parity/high-latitude-probe.json Kotlin values for the >48-degree probe set

Run:  python3 scripts/adhan-parity/generate.py
Needs: java + javac on PATH. The Adhan JAR is downloaded from Maven Central on first run and
is gitignored — the pin lives in scripts/reference-versions.json.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent
REPO_ROOT = HERE.parent.parent
VECTORS_DIR = REPO_ROOT / "test-vectors" / "prayer-times"
REFERENCE_VERSIONS = json.loads((HERE.parent / "reference-versions.json").read_text())
JAR = HERE / f"adhan-{REFERENCE_VERSIONS['adhan-java']}.jar"
RUNNER_CLASS = HERE / "AdhanKotlinRunner.class"
REFERENCE = f"Adhan-Kotlin {REFERENCE_VERSIONS['adhan-java-maven']}"


def ensure_runner() -> None:
    if not JAR.exists():
        url = REFERENCE_VERSIONS["adhan-java-url"]
        print(f"[info] downloading {JAR.name}")
        subprocess.run(["curl", "-fsSL", "-o", str(JAR), url], check=True)
    if not RUNNER_CLASS.exists() or RUNNER_CLASS.stat().st_mtime < (HERE / "AdhanKotlinRunner.java").stat().st_mtime:
        print("[info] compiling AdhanKotlinRunner")
        subprocess.run(
            ["javac", "-cp", str(JAR), "-d", str(HERE), str(HERE / "AdhanKotlinRunner.java")],
            check=True,
        )


def run_case(case: dict) -> dict:
    result = subprocess.run(
        [
            "java", "-cp", f"{HERE}:{JAR}", "AdhanKotlinRunner",
            str(case["latitude"]), str(case["longitude"]),
            case["date"], case["timezone"], case["method"],
        ],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def main() -> int:
    ensure_runner()
    spec = json.loads((HERE / "cases.json").read_text())

    by_method: dict[str, list[dict]] = {}
    for city in spec["cities"]:
        times = run_case(city)
        if times.get("unavailable"):
            print(f"[error] {city['id']}: Adhan-Kotlin has no times for this case", file=sys.stderr)
            return 1
        times.pop("high_latitude_rule", None)
        case = {
            "description": city["description"],
            "input": {
                key: city[key]
                for key in ("latitude", "longitude", "date", "timezone", "elevation_meters")
                if key in city
            },
            "expected": times,
        }
        by_method.setdefault(city["method"], []).append(case)

    VECTORS_DIR.mkdir(parents=True, exist_ok=True)
    for method, cases in sorted(by_method.items()):
        path = VECTORS_DIR / f"{method.lower()}.json"
        path.write_text(
            json.dumps(
                {
                    "method": method,
                    "tolerance_minutes": 1,
                    "reference": REFERENCE,
                    "cases": cases,
                },
                indent=2,
            )
            + "\n"
        )
        print(f"[ok] {path.relative_to(REPO_ROOT)} — {len(cases)} case(s)")

    probes = []
    for probe in spec["high_latitude_probe"]:
        times = run_case(probe)
        probes.append({**{k: probe[k] for k in ("id", "description", "latitude", "longitude", "date", "timezone", "method")}, "expected": times})
    (HERE / "high-latitude-probe.json").write_text(
        json.dumps({"reference": REFERENCE, "cases": probes}, indent=2) + "\n"
    )
    print(f"[ok] high-latitude-probe.json — {len(probes)} case(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
