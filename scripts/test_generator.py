#!/usr/bin/env python3
"""
Self-tests for the vector generator. Three criteria from TODOS.md:
  1. Makkah 2026-03-21 MWL matches known golden values (requires Java).
  2. Disagreement detection flags when sources differ >1 min.
  3. schema.json validation passes on all generated vector files.

Run: python3 scripts/test_generator.py
     python3 scripts/test_generator.py --skip-java   (dev machines without JRE)
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any

SCRIPTS_DIR = Path(__file__).parent
REPO_ROOT = SCRIPTS_DIR.parent
VECTORS_DIR = REPO_ROOT / "test-vectors" / "prayer-times"
SCHEMA_PATH = REPO_ROOT / "test-vectors" / "schema.json"

sys.path.insert(0, str(SCRIPTS_DIR))

# Makkah 2026-03-21 MWL golden values from architecture-design.md
GOLDEN: dict[str, str] = {
    "fajr": "05:12",
    "sunrise": "06:23",
    "dhuhr": "12:28",
    "asr_shafii": "15:49",
    "asr_hanafi": "16:22",
    "maghrib": "18:33",
    "isha": "19:43",
}
GOLDEN_INPUT = {
    "latitude": 21.4225,
    "longitude": 39.8262,
    "date": "2026-03-21",
    "timezone": "Asia/Riyadh",
    "elevation_meters": 277,
}

SKIP_JAVA = False  # set by --skip-java flag


def time_diff_minutes(a: str, b: str) -> int:
    ah, am = (int(x) for x in a.split(":"))
    bh, bm = (int(x) for x in b.split(":"))
    return abs((ah * 60 + am) - (bh * 60 + bm))


class TestGoldenValues(unittest.TestCase):
    """Self-test 1: Adhan produces Makkah 2026-03-21 MWL golden values within ±1 min."""

    def setUp(self) -> None:
        if SKIP_JAVA:
            self.skipTest("--skip-java: skipping Adhan golden values check")

    def test_adhan_makkah_mwl(self) -> None:
        from generate_vectors import run_adhan

        result = run_adhan(
            GOLDEN_INPUT["latitude"],
            GOLDEN_INPUT["longitude"],
            GOLDEN_INPUT["date"],
            GOLDEN_INPUT["timezone"],
            "MWL",
        )
        if result is None:
            self.skipTest("Adhan JAR unavailable (Java not installed)")

        tolerance = 1
        failures: list[str] = []
        for prayer, expected in GOLDEN.items():
            diff = time_diff_minutes(result[prayer], expected)
            if diff > tolerance:
                failures.append(f"{prayer}: got {result[prayer]}, expected {expected} (diff {diff} min)")

        if failures:
            self.fail("Adhan output outside ±1 min golden values:\n  " + "\n  ".join(failures))


class TestDisagreementDetection(unittest.TestCase):
    """Self-test 2: cross_validate flags when sources differ >1 min."""

    def test_within_tolerance_no_disagreement(self) -> None:
        from generate_vectors import cross_validate

        source_a = {"fajr": "05:12", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:49", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:43"}
        source_b = {"fajr": "05:12", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:49", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:43"}

        result = cross_validate(source_a, source_b, "test", "2026-03-21", "MWL")
        self.assertEqual(result, [], "Identical sources should produce no disagreements")

    def test_1min_delta_no_flag(self) -> None:
        from generate_vectors import cross_validate

        source_a = {"fajr": "05:12", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:49", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:43"}
        source_b = dict(source_a)
        source_b["fajr"] = "05:13"  # exactly 1 min difference

        result = cross_validate(source_a, source_b, "test", "2026-03-21", "MWL", threshold_minutes=1)
        self.assertEqual(result, [], "1-min difference should not flag at threshold=1")

    def test_2min_delta_flags(self) -> None:
        from generate_vectors import cross_validate

        source_a = {"fajr": "05:12", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:49", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:43"}
        source_b = dict(source_a)
        source_b["isha"] = "19:45"  # 2 min difference

        result = cross_validate(source_a, source_b, "test", "2026-03-21", "MWL", threshold_minutes=1)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["prayer"], "isha")
        self.assertEqual(result[0]["diff_minutes"], 2)

    def test_multiple_flags(self) -> None:
        from generate_vectors import cross_validate

        source_a = {"fajr": "05:12", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:49", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:43"}
        source_b = {"fajr": "05:20", "sunrise": "06:23", "dhuhr": "12:28",
                    "asr_shafii": "15:59", "asr_hanafi": "16:22", "maghrib": "18:33", "isha": "19:57"}

        result = cross_validate(source_a, source_b, "test", "2026-03-21", "MWL", threshold_minutes=1)
        flagged = {r["prayer"] for r in result}
        self.assertIn("fajr", flagged)    # 8 min diff
        self.assertIn("asr_shafii", flagged)  # 10 min diff
        self.assertIn("isha", flagged)    # 14 min diff
        self.assertNotIn("sunrise", flagged)
        self.assertNotIn("dhuhr", flagged)
        self.assertNotIn("maghrib", flagged)

    def test_diff_minutes_calculation(self) -> None:
        from generate_vectors import time_diff_minutes

        self.assertEqual(time_diff_minutes("05:12", "05:12"), 0)
        self.assertEqual(time_diff_minutes("05:12", "05:15"), 3)
        self.assertEqual(time_diff_minutes("05:15", "05:12"), 3)
        self.assertEqual(time_diff_minutes("23:58", "00:02"), 4)


class TestSchemaValidation(unittest.TestCase):
    """Self-test 3: schema.json validates generated vector files correctly."""

    def _load_schema(self) -> dict[str, Any]:
        if not SCHEMA_PATH.exists():
            self.skipTest(f"schema.json not found at {SCHEMA_PATH}")
        return json.loads(SCHEMA_PATH.read_text())

    def _validate(self, instance: Any, schema: dict[str, Any]) -> None:
        try:
            import jsonschema  # type: ignore[import]
        except ImportError:
            self.skipTest("jsonschema not installed")
        jsonschema.validate(instance=instance, schema=schema)

    def _sample_vector(self) -> dict[str, Any]:
        return {
            "method": "MWL",
            "tolerance_minutes": 1,
            "reference": "Adhan 1.2.1",
            "cases": [{
                "description": "Makkah 2026-03-21",
                "input": {
                    "latitude": 21.4225,
                    "longitude": 39.8262,
                    "date": "2026-03-21",
                    "timezone": "Asia/Riyadh",
                    "elevation_meters": 277.0,
                },
                "expected": {
                    "fajr": "05:12",
                    "sunrise": "06:23",
                    "dhuhr": "12:28",
                    "asr_shafii": "15:49",
                    "asr_hanafi": "16:22",
                    "maghrib": "18:33",
                    "isha": "19:43",
                },
            }],
        }

    def test_valid_vector_passes(self) -> None:
        schema = self._load_schema()
        self._validate(self._sample_vector(), schema)

    def test_missing_required_field_fails(self) -> None:
        import jsonschema  # type: ignore[import]
        schema = self._load_schema()
        bad = self._sample_vector()
        del bad["method"]
        with self.assertRaises(jsonschema.ValidationError):
            self._validate(bad, schema)

    def test_unknown_method_fails(self) -> None:
        import jsonschema  # type: ignore[import]
        schema = self._load_schema()
        bad = self._sample_vector()
        bad["method"] = "UNKNOWN_METHOD"
        with self.assertRaises(jsonschema.ValidationError):
            self._validate(bad, schema)

    def test_invalid_time_format_fails(self) -> None:
        import jsonschema  # type: ignore[import]
        schema = self._load_schema()
        bad = self._sample_vector()
        bad["cases"][0]["expected"]["fajr"] = "5:12"  # missing leading zero
        with self.assertRaises(jsonschema.ValidationError):
            self._validate(bad, schema)

    def test_invalid_date_format_fails(self) -> None:
        import jsonschema  # type: ignore[import]
        schema = self._load_schema()
        bad = self._sample_vector()
        bad["cases"][0]["input"]["date"] = "21-03-2026"  # wrong order
        with self.assertRaises(jsonschema.ValidationError):
            self._validate(bad, schema)

    def test_extra_field_fails(self) -> None:
        import jsonschema  # type: ignore[import]
        schema = self._load_schema()
        bad = self._sample_vector()
        bad["unknown_field"] = "value"
        with self.assertRaises(jsonschema.ValidationError):
            self._validate(bad, schema)

    def test_all_generated_files_valid(self) -> None:
        """Validate every file already present in test-vectors/prayer-times/."""
        schema = self._load_schema()
        if not VECTORS_DIR.exists():
            return  # nothing generated yet — not a failure
        files = list(VECTORS_DIR.glob("*.json"))
        if not files:
            return
        for path in files:
            with self.subTest(file=path.name):
                instance = json.loads(path.read_text())
                self._validate(instance, schema)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--skip-java", action="store_true")
    known, remaining = parser.parse_known_args()
    SKIP_JAVA = known.skip_java

    # Pass remaining args to unittest
    sys.argv = [sys.argv[0]] + remaining
    unittest.main(verbosity=2)
