#!/usr/bin/env python3
"""
generate_vectors.py — aynama prayer-times test vector generator.

Primary source : Adhan 1.2.1 (Java JAR subprocess) — matches production library.
Secondary source: PrayTimes.py 2.3.2             — cross-validation only.

Output : test-vectors/prayer-times/<city>_<method>.json
Disagreements: scripts/disagreements.json  (>1 min delta between sources)

Adhan-supported methods (v1 scope):
  MWL, ISNA, UMM_AL_QURA, EGYPTIAN, KARACHI,
  DUBAI, KUWAIT, QATAR, SINGAPORE, MOON_SIGHTING_COMMITTEE

Not in Adhan 1.2.1 — excluded from v1 vectors: TEHRAN, GULF, FRANCE, TURKEY

Usage:
  python3 scripts/generate_vectors.py              # generate all configured cities
  python3 scripts/generate_vectors.py --city makkah_mwl
"""

from __future__ import annotations

import datetime
import json
import os
import subprocess
import sys
import zoneinfo
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).parent.parent
SCRIPTS_DIR = Path(__file__).parent
VECTORS_DIR = REPO_ROOT / "test-vectors" / "prayer-times"
SCHEMA_PATH = REPO_ROOT / "test-vectors" / "schema.json"
JAR_PATH = SCRIPTS_DIR / "adhan-1.2.1.jar"
ADHAN_RUNNER = SCRIPTS_DIR / "AdhanRunner.class"
DISAGREEMENTS_PATH = SCRIPTS_DIR / "disagreements.json"

# PrayTimes.py method name mapping (schema name → praytimes key)
PRAYTIMES_METHOD_MAP: dict[str, str] = {
    "MWL": "MWL",
    "ISNA": "ISNA",
    "UMM_AL_QURA": "Makkah",
    "EGYPTIAN": "Egypt",
    "KARACHI": "Karachi",
    "TEHRAN": "Tehran",
}

# City configurations used for vector generation
CITY_CONFIGS: list[dict[str, Any]] = [
    {
        "id": "makkah_mwl",
        "description": "Makkah, Saudi Arabia — MWL method",
        "input": {
            "latitude": 21.4225,
            "longitude": 39.8262,
            "date": "2026-03-21",
            "timezone": "Asia/Riyadh",
            "elevation_meters": 277,
        },
        "method": "MWL",
        "tolerance_minutes": 1,
        "reference": "Adhan 1.2.1 (com.batoulapps.adhan:adhan:1.2.1)",
    },
]


def adhan_available() -> bool:
    return JAR_PATH.exists() and ADHAN_RUNNER.exists()


def download_jar() -> bool:
    """Download the Adhan JAR from Maven Central if not present. Returns True on success."""
    if JAR_PATH.exists():
        return True
    ref = json.loads((SCRIPTS_DIR / "reference-versions.json").read_text())
    url = ref["adhan-java-url"]
    print(f"[INFO] Downloading {JAR_PATH.name} from Maven Central…")
    result = subprocess.run(
        ["curl", "-fsSL", "-o", str(JAR_PATH), url],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"[ERROR] Download failed: {result.stderr}", file=sys.stderr)
        return False
    print(f"[INFO] Downloaded {JAR_PATH.name} ({JAR_PATH.stat().st_size} bytes)")
    return True


def compile_adhan_runner() -> bool:
    """Compile AdhanRunner.java against the JAR. Returns True on success."""
    java_src = SCRIPTS_DIR / "AdhanRunner.java"
    if not java_src.exists():
        print(f"[ERROR] {java_src} not found", file=sys.stderr)
        return False
    if not download_jar():
        return False
    result = subprocess.run(
        ["javac", "-cp", str(JAR_PATH), str(java_src), "-d", str(SCRIPTS_DIR)],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"[ERROR] javac failed:\n{result.stderr}", file=sys.stderr)
        return False
    return True


def run_adhan(lat: float, lon: float, date: str, timezone: str, method: str) -> dict[str, str] | None:
    """Call AdhanRunner subprocess; return prayer times dict or None on failure."""
    if not adhan_available():
        if not compile_adhan_runner():
            return None
    y, m, d = date.split("-")
    result = subprocess.run(
        ["java", "-cp", f"{SCRIPTS_DIR}:{JAR_PATH}", "AdhanRunner",
         str(lat), str(lon), y, m, d, timezone, method],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"[WARN] Adhan failed for {method}: {result.stderr.strip()}", file=sys.stderr)
        return None
    try:
        return json.loads(result.stdout.strip())
    except json.JSONDecodeError as e:
        print(f"[ERROR] Adhan output not JSON: {result.stdout!r}: {e}", file=sys.stderr)
        return None


def run_praytimes(lat: float, lon: float, date: str, timezone: str, method: str) -> dict[str, str] | None:
    """Calculate prayer times via PrayTimes.py; return dict or None if method unsupported."""
    pt_method = PRAYTIMES_METHOD_MAP.get(method)
    if pt_method is None:
        return None

    try:
        import praytimes.praytimes as pt_module  # type: ignore[import]
    except ImportError:
        print("[WARN] praytimes not installed; skipping PrayTimes.py cross-validation", file=sys.stderr)
        return None

    y, mo, d = (int(x) for x in date.split("-"))
    tz = zoneinfo.ZoneInfo(timezone)
    dt = datetime.datetime(y, mo, d, tzinfo=tz)
    offset = dt.utcoffset().total_seconds() / 3600  # type: ignore[union-attr]

    # Workaround: PrayTimes.__init__ shadows its 'method' parameter with the loop
    # variable, so it always loads the last method (Jafari). Update settings manually.
    pt = pt_module.PrayTimes()
    pt.settings.update(pt.methods[pt_method]["params"])

    def hhmm(h: float) -> str:
        h = h + 0.5 / 60  # round to nearest minute
        return "%02d:%02d" % (int(h) % 24, int((h % 1) * 60))

    pt.settings["asr"] = "Standard"
    shafi = pt.getTimes((y, mo, d), (lat, lon), offset, 0, "Float")

    pt.settings["asr"] = "Hanafi"
    hanafi = pt.getTimes((y, mo, d), (lat, lon), offset, 0, "Float")

    return {
        "fajr": hhmm(shafi["fajr"]),
        "sunrise": hhmm(shafi["sunrise"]),
        "dhuhr": hhmm(shafi["dhuhr"]),
        "asr_shafii": hhmm(shafi["asr"]),
        "asr_hanafi": hhmm(hanafi["asr"]),
        "maghrib": hhmm(shafi["maghrib"]),
        "isha": hhmm(shafi["isha"]),
    }


def time_diff_minutes(a: str, b: str) -> int:
    """Return absolute difference in minutes between two HH:mm strings.
    Takes the shorter path around the clock to handle midnight wraparound."""
    ah, am = (int(x) for x in a.split(":"))
    bh, bm = (int(x) for x in b.split(":"))
    diff = abs((ah * 60 + am) - (bh * 60 + bm))
    return min(diff, 24 * 60 - diff)


def cross_validate(
    adhan: dict[str, str],
    praytimes: dict[str, str],
    city_id: str,
    date: str,
    method: str,
    threshold_minutes: int = 1,
) -> list[dict[str, Any]]:
    """Compare Adhan and PrayTimes times; return list of disagreement records."""
    disagreements: list[dict[str, Any]] = []
    for key in ("fajr", "sunrise", "dhuhr", "asr_shafii", "asr_hanafi", "maghrib", "isha"):
        diff = time_diff_minutes(adhan[key], praytimes[key])
        if diff > threshold_minutes:
            disagreements.append({
                "city": city_id,
                "date": date,
                "method": method,
                "prayer": key,
                "adhan": adhan[key],
                "praytimes": praytimes[key],
                "diff_minutes": diff,
            })
    return disagreements


def load_disagreements() -> list[dict[str, Any]]:
    if DISAGREEMENTS_PATH.exists():
        return json.loads(DISAGREEMENTS_PATH.read_text())
    return []


def save_disagreements(records: list[dict[str, Any]]) -> None:
    DISAGREEMENTS_PATH.write_text(json.dumps(records, indent=2))


def generate_vector(config: dict[str, Any]) -> Path | None:
    """Generate one vector file; return path on success or None on failure."""
    city_id = config["id"]
    method = config["method"]
    inp = config["input"]
    lat, lon = inp["latitude"], inp["longitude"]
    date, timezone = inp["date"], inp["timezone"]

    print(f"[GEN] {city_id} ({method}) {date}")

    adhan_times = run_adhan(lat, lon, date, timezone, method)
    if adhan_times is None:
        print(f"[SKIP] {city_id}: Adhan unavailable (Java not found or method unsupported)", file=sys.stderr)
        return None

    pt_times = run_praytimes(lat, lon, date, timezone, method)
    if pt_times is not None:
        new_disagreements = cross_validate(adhan_times, pt_times, city_id, date, method)
        if new_disagreements:
            existing = load_disagreements()
            existing.extend(new_disagreements)
            save_disagreements(existing)
            for rec in new_disagreements:
                print(f"  [DISAGREE] {rec['prayer']}: adhan={rec['adhan']} praytimes={rec['praytimes']} ({rec['diff_minutes']} min)")

    case: dict[str, Any] = {"input": dict(inp), "expected": adhan_times}
    if config.get("description"):
        case["description"] = config["description"]

    vector = {
        "method": method,
        "tolerance_minutes": config["tolerance_minutes"],
        "reference": config["reference"],
        "cases": [case],
    }

    VECTORS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = VECTORS_DIR / f"{city_id}.json"
    out_path.write_text(json.dumps(vector, indent=2) + "\n")
    print(f"  -> {out_path.relative_to(REPO_ROOT)}")
    return out_path


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(description="Generate prayer-times test vectors")
    parser.add_argument("--city", help="Generate only this city ID (e.g. makkah_mwl)")
    args = parser.parse_args()

    configs = CITY_CONFIGS
    if args.city:
        configs = [c for c in CITY_CONFIGS if c["id"] == args.city]
        if not configs:
            sys.exit(f"Unknown city: {args.city}")

    generated = []
    for config in configs:
        path = generate_vector(config)
        if path:
            generated.append(path)

    print(f"\n[DONE] Generated {len(generated)}/{len(configs)} vector(s)")


if __name__ == "__main__":
    main()
