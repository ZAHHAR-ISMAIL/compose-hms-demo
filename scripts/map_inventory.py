#!/usr/bin/env python3
"""Inventory SDK coupling before migrating a large application. Does not edit sources."""
import argparse
import json
import re
from pathlib import Path

SKIP = {"build", ".git", ".gradle", ".gradle-local", ".idea", ".kotlin", "node_modules"}
PATTERNS = {
    "google_compose": r"com\.google\.maps\.android\.compose\b",
    "google_maps": r"com\.google\.android\.gms\.maps\b",
    "google_location": r"com\.google\.android\.gms\.location\b",
    "huawei_maps": r"com\.huawei\.hms\.maps\b",
    "huawei_location": r"com\.huawei\.hms\.location\b",
    "places": r"com\.google\.android\.libraries\.places\b|com\.huawei\.hms\.site\b",
    "map_utilities": r"com\.google\.maps\.android\.(?!compose\b|ktx\b)\w+",
    "native_escape": r"\b(MapEffect|GoogleMapOptions|MapView|SupportMapFragment|Clustering|StreetView|AdvancedMarker)\b",
    "web_services": r"maps\.googleapis\.com|\b(DirectionsApi|GeocodingApi|DistanceMatrixApi)\b",
    "sdk_dependency": r"play-services-(maps|location)|com\.google\.maps\.android:|com\.huawei\.hms:(maps|location)",
}


def source_files(root):
    return sorted(p for p in root.rglob("*") if p.is_file() and p.suffix in {".kt", ".java", ".xml", ".gradle", ".kts"}
                  and not SKIP.intersection(p.relative_to(root).parts))


def inventory(root):
    result = {category: [] for category in PATTERNS}
    for path in source_files(root):
        for number, line in enumerate(path.read_text(errors="replace").splitlines(), 1):
            for category, pattern in PATTERNS.items():
                if re.search(pattern, line):
                    result[category].append({"file": str(path.relative_to(root)), "line": number, "reference": line.strip()})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--check-shared", action="store_true", help="Fail if shared application/API code imports either native map SDK")
    args = parser.parse_args()
    result = inventory(args.root.resolve())
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        for category, references in result.items():
            print(f"{category}: {len(references)} references in {len({r['file'] for r in references})} files")
            for ref in references:
                print(f"  {ref['file']}:{ref['line']}: {ref['reference']}")
    if args.check_shared:
        violations = [r for category in ["google_compose", "google_maps", "huawei_maps", "google_location", "huawei_location"]
                      for r in result[category] if r["file"].startswith("app/src/main/")]
        if violations:
            raise SystemExit(f"Provider SDK coupling in shared sources: {violations}")


if __name__ == "__main__":
    main()
