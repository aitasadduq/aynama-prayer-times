#!/usr/bin/env python3
"""
Generate the offline coordinates -> IANA time zone lookup used by city search (DS32).

City search already knows the country (the Geocoder returns it), so the only question is which
of that country's zones a point falls in. For each country with more than one zone this builds a
quadtree over the zone boundaries of that country alone: land outside the country and the sea
are "don't care", so coastlines and international borders cost nothing and only the internal
boundaries (Madrid/Canary, Eastern/Central, WIB/WITA, ...) are subdivided, down to ~1.2 km.

Writes:
  android/shared-logic/src/main/resources/timezone-lookup.bin
  android/shared-logic/src/test/resources/timezone-lookup-cases.tsv  every zone.tab location

Format of the .bin (big-endian):
  "TZL1", u16 country count, then per country:
    2 ASCII bytes country code, u8 zone count, per zone (u8 length, ASCII id),
    u32 tree length, tree bytes.
  The tree is a pre-order walk of a quadtree whose root is the square lon -180..180,
  lat -180..180. 0xFF is an inner node followed by its four children (SW, SE, NW, NE);
  any other byte is a leaf holding a zone index. A single-zone country is the one byte 0.

Run:  uv run --with shapely python scripts/timezone-lookup/generate.py
Inputs (pinned in scripts/reference-versions.json) are downloaded on first run and gitignored.
"""

from __future__ import annotations

import hashlib
import io
import json
import struct
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

import shapely
from shapely.geometry import box, shape
from shapely.prepared import prep

HERE = Path(__file__).parent
REPO_ROOT = HERE.parent.parent
VERSIONS = json.loads((HERE.parent / "reference-versions.json").read_text())
OUT = REPO_ROOT / "android/shared-logic/src/main/resources/timezone-lookup.bin"
CASES = REPO_ROOT / "android/shared-logic/src/test/resources/timezone-lookup-cases.tsv"

MAX_DEPTH = 15  # 360 / 2^15 = 0.011 degrees, about 1.2 km
INNER = 0xFF


def fetch(url_key: str, sha_key: str) -> Path:
    url = VERSIONS[url_key]
    path = HERE / url.rsplit("/", 1)[1]
    if not path.exists():
        print(f"downloading {url}", file=sys.stderr)
        urllib.request.urlretrieve(url, path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != VERSIONS[sha_key]:
        sys.exit(f"{path.name}: sha256 {digest} does not match the pin")
    return path


def parse_coord(text: str, degree_digits: int) -> float:
    sign = -1 if text[0] == "-" else 1
    digits = text[1:]
    value = int(digits[:degree_digits])
    rest = digits[degree_digits:]
    for i, divisor in enumerate((60, 3600)):
        if len(rest) >= 2 * (i + 1):
            value += int(rest[2 * i:2 * i + 2]) / divisor
    return sign * value


def read_zone_tab(tzdata: Path) -> list[tuple[str, float, float, str]]:
    with tarfile.open(tzdata) as tar:
        text = tar.extractfile("zone.tab").read().decode()
    rows = []
    for line in text.splitlines():
        if line.startswith("#") or not line.strip():
            continue
        country, coords, zone = line.split("\t")[:3]
        split = max(coords.rfind("+"), coords.rfind("-"))
        lat = parse_coord(coords[:split], 2)
        lon = parse_coord(coords[split:], 3)
        rows.append((country, lat, lon, zone))
    return rows


def without_overlaps(zone_geoms: list) -> list:
    # The boundary data overlaps Asia/Shanghai and Asia/Urumqi across Xinjiang, where both
    # clocks are in use. The smaller zone wins, as zone.tab's own Urumqi entry does.
    by_area = sorted(range(len(zone_geoms)), key=lambda i: zone_geoms[i].area)
    result = list(zone_geoms)
    for rank, i in enumerate(by_area):
        for smaller in by_area[:rank]:
            if result[i].intersection(zone_geoms[smaller]).area > 0:
                result[i] = result[i].difference(zone_geoms[smaller])
    return result


def build_tree(zone_geoms: list) -> bytes:
    zone_geoms = without_overlaps(zone_geoms)
    prepared = [prep(g) for g in zone_geoms]
    parts, owners = [], []
    for index, geom in enumerate(zone_geoms):
        for part in getattr(geom, "geoms", [geom]):
            parts.append(part)
            owners.append(index)
    nearest_index = shapely.STRtree(parts)

    def nearest_zone(x: float, y: float) -> int:
        return owners[int(nearest_index.query_nearest(shapely.Point(x, y))[0])]

    def build(x: float, y: float, size: float, depth: int, candidates: list[int]):
        cell = box(x, y, x + size, y + size)
        hits = [z for z in candidates if prepared[z].intersects(cell)]
        if not hits:
            return None
        if len(hits) == 1:
            return hits[0]
        cx, cy = x + size / 2, y + size / 2
        if depth == MAX_DEPTH:
            centre = shapely.Point(cx, cy)
            inside = [z for z in hits if prepared[z].contains(centre)]
            return inside[0] if inside else nearest_zone(cx, cy)
        half = size / 2
        origins = [(x, y), (cx, y), (x, cy), (cx, cy)]
        children = [build(ox, oy, half, depth + 1, hits) for ox, oy in origins]
        zones = {c for c in children if not isinstance(c, list)} - {None}
        if not any(isinstance(c, list) for c in children) and len(zones) <= 1:
            return next(iter(zones), None)
        # A don't-care quadrant beside real zones takes the nearest one, so a point the
        # Geocoder puts just offshore or over a border still lands in its own country.
        return [
            nearest_zone(ox + half / 2, oy + half / 2) if child is None else child
            for child, (ox, oy) in zip(children, origins)
        ]

    out = bytearray()

    def emit(node):
        if isinstance(node, list):
            out.append(INNER)
            for child in node:
                emit(child)
        else:
            out.append(node)

    emit(build(-180.0, -180.0, 360.0, 0, list(range(len(zone_geoms)))))
    return bytes(out)


def lookup(zones: list[str], tree: bytes, lat: float, lon: float) -> str:
    x, y, size, pos = -180.0, -180.0, 360.0, 0
    while tree[pos] == INNER:
        size /= 2
        quadrant = (2 if lat >= y + size else 0) + (1 if lon >= x + size else 0)
        if quadrant & 1:
            x += size
        if quadrant & 2:
            y += size
        pos += 1
        for _ in range(quadrant):
            pending = 1
            while pending:
                pending += 3 if tree[pos] == INNER else -1
                pos += 1
    return zones[tree[pos]]


def main() -> None:
    zone_tab = read_zone_tab(fetch("tzdata-url", "tzdata-sha256"))
    by_country: dict[str, list[str]] = {}
    for country, _, _, zone in zone_tab:
        by_country.setdefault(country, []).append(zone)
    wanted = {z for zones in by_country.values() if len(zones) > 1 for z in zones}

    print("reading boundaries", file=sys.stderr)
    with zipfile.ZipFile(fetch("timezone-boundary-builder-url", "timezone-boundary-builder-sha256")) as archive:
        collection = json.load(io.TextIOWrapper(archive.open("combined.json")))
    geoms = {
        f["properties"]["tzid"]: shape(f["geometry"])
        for f in collection["features"]
        if f["properties"]["tzid"] in wanted
    }
    missing = wanted - geoms.keys()
    if missing:
        sys.exit(f"no boundary for {sorted(missing)}")

    trees = {}
    for country in sorted(by_country):
        zones = sorted(by_country[country])
        if len(zones) == 1:
            trees[country] = (zones, b"\x00")
            continue
        print(f"{country}: {len(zones)} zones", file=sys.stderr)
        trees[country] = (zones, build_tree([geoms[z] for z in zones]))

    blob = bytearray(b"TZL1")
    blob += struct.pack(">H", len(trees))
    for country, (zones, tree) in trees.items():
        blob += country.encode("ascii") + struct.pack(">B", len(zones))
        for zone in zones:
            blob += struct.pack(">B", len(zone)) + zone.encode("ascii")
        blob += struct.pack(">I", len(tree)) + tree
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(bytes(blob))
    print(f"wrote {OUT.relative_to(REPO_ROOT)}: {len(blob)} bytes", file=sys.stderr)

    wrong = [
        (country, zone, lookup(*trees[country], lat, lon))
        for country, lat, lon, zone in zone_tab
        if lookup(*trees[country], lat, lon) != zone
    ]
    for country, expected, got in wrong:
        print(f"MISMATCH {country} {expected}: got {got}", file=sys.stderr)
    if wrong:
        sys.exit(1)

    CASES.parent.mkdir(parents=True, exist_ok=True)
    CASES.write_text(
        "# country\tlatitude\tlongitude\tzone - every zone.tab principal location, tzdata "
        + VERSIONS["tzdata"] + "\n"
        + "".join(f"{c}\t{lat:.4f}\t{lon:.4f}\t{z}\n" for c, lat, lon, z in zone_tab)
    )
    print(f"all {len(zone_tab)} zone.tab locations resolve to their own zone", file=sys.stderr)


if __name__ == "__main__":
    main()
