#!/usr/bin/env python3
"""Dry-run Kotlin map import/FQN migration. --apply writes only when all references are supported."""
import argparse
import difflib
import re
from pathlib import Path
from map_inventory import SKIP

MAPPING = {
    **{f"com.google.maps.android.clustering.{name}": f"com.demo.maps.clustering.{name}"
       for name in ["ClusterItem", "Cluster"]},
    **{f"com.google.android.gms.maps.{name}": f"com.demo.maps.{name}"
       for name in ["CameraUpdate", "CameraUpdateFactory", "MapsInitializer"]},
    **{f"com.google.android.gms.maps.model.{name}": f"com.demo.maps.model.{name}"
       for name in ["LatLng", "LatLngBounds", "CameraPosition", "MapStyleOptions", "BitmapDescriptor", "BitmapDescriptorFactory", "Marker", "Polygon", "Polyline", "Circle"]},
    **{f"com.google.maps.android.compose.{name}": f"com.demo.maps.compose.{name}"
       for name in ["GoogleMapComposable", "GoogleMap", "CameraPositionState", "CameraMoveStartedReason", "MarkerState", "MapProperties", "MapUiSettings", "MapType", "Marker", "MarkerComposable", "Polygon", "Polyline", "Circle", "rememberCameraPositionState", "rememberMarkerState", "rememberUpdatedMarkerState", "currentCameraPositionState"]},
}
# Utility imports (clustering, renderers, geometry, KTX, etc.) also retain Google SDK coupling.
# They must stop a shared-source migration until a compatible implementation is provided.
REFERENCE = re.compile(r"\bcom\.(?:google\.android\.gms\.maps|google\.maps\.android)\.[\w.*]+")
REPLACE = re.compile(r"(?<![\w.])(?:" + "|".join(re.escape(k) for k in sorted(MAPPING, key=len, reverse=True)) + r")(?!\w)")


def code_mask(source):
    """Mask Kotlin comments/literals, including nested block comments; keep original offsets."""
    result = list(source)
    index = 0
    while index < len(source):
        start = index
        if source.startswith("//", index):
            index = source.find("\n", index)
            if index == -1:
                index = len(source)
        elif source.startswith("/*", index):
            index += 2
            depth = 1
            while index < len(source) and depth:
                if source.startswith("/*", index):
                    depth += 1; index += 2
                elif source.startswith("*/", index):
                    depth -= 1; index += 2
                else:
                    index += 1
        elif source.startswith('"""', index):
            end = source.find('"""', index + 3)
            index = end + 3 if end != -1 else len(source)
        elif source[index] in {'"', "'", "`"}:
            quote = source[index]
            index += 1
            while index < len(source):
                if source[index] == "\\" and quote != "`":
                    index += 2
                elif source[index] == quote:
                    index += 1; break
                else:
                    index += 1
        else:
            index += 1
            continue
        for offset in range(start, min(index, len(source))):
            if source[offset] != "\n":
                result[offset] = " "
    return "".join(result)


def migrate(source):
    masked = code_mask(source)
    matches = list(REPLACE.finditer(masked))
    updated = source
    for match in reversed(matches):
        updated = updated[:match.start()] + MAPPING[match.group()] + updated[match.end():]
    unsupported = sorted(set(REFERENCE.findall(code_mask(updated))))
    return updated, unsupported


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="Feature source directory; exclude native provider implementations")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    changes, unsupported = [], []
    for path in sorted(args.root.rglob("*.kt")):
        if SKIP.intersection(path.relative_to(args.root).parts):
            continue
        before = path.read_text()
        after, remaining = migrate(before)
        unsupported.extend(f"{path}: {reference}" for reference in remaining)
        if before != after:
            changes.append((path, before, after))
    if unsupported:
        raise SystemExit("Manual adapters required; no files were changed:\n" + "\n".join(unsupported))
    for path, before, after in changes:
        print("".join(difflib.unified_diff(before.splitlines(keepends=True), after.splitlines(keepends=True), fromfile=str(path), tofile=str(path))), end="")
        if args.apply:
            path.write_text(after)
    print(f"{'Updated' if args.apply else 'Would update'} {len(changes)} Kotlin files. Java/XML and SDK-dependent binaries require separate review.")


if __name__ == "__main__":
    main()
