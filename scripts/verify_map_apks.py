#!/usr/bin/env python3
"""Verify actual DEX class definitions, rather than matching incidental SDK strings."""
import argparse
import re
import struct
import zipfile
from pathlib import Path


def dex_classes(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Expected a standard DEX file")
    uint = lambda offset: struct.unpack_from("<I", data, offset)[0]
    strings = uint(0x3C)
    types = uint(0x44)
    count, offset = uint(0x60), uint(0x64)
    for index in range(count):
        type_index = uint(offset + index * 32)
        string_index = uint(types + type_index * 4)
        cursor = uint(strings + string_index * 4)
        while data[cursor] & 0x80:
            cursor += 1
        cursor += 1  # Skip the last ULEB128 length byte.
        end = data.index(0, cursor)
        yield data[cursor:end].decode("utf-8", errors="replace")


def verify(apk, provider):
    with zipfile.ZipFile(apk) as archive:
        classes = {name for file in archive.namelist() if re.fullmatch(r"classes\d*\.dex", file)
                   for name in dex_classes(archive.read(file))}
    forbidden = ("Lcom/google/android/gms/maps/", "Lcom/google/android/gms/location/", "Lcom/google/maps/android/") if provider == "huawei" else ("Lcom/huawei/hms/maps/", "Lcom/huawei/hms/location/")
    offending = sorted(name for name in classes if name.startswith(forbidden))
    required = "Lcom/demo/maps/compose/MapsComposeKt;"
    if offending or required not in classes:
        raise SystemExit(f"{apk}: isolation failed; Compose API present={required in classes}; forbidden classes={offending[:10]}")
    # Aliases must not accidentally become replacement classes on the Google path. Huawei retains
    # these compatible implementations, while Google must package the official implementations.
    compatibility_types = {
        "Lcom/demo/maps/CameraUpdate;",
        "Lcom/demo/maps/CameraUpdateFactory;",
        "Lcom/demo/maps/compose/internal/CameraController;",
        "Lcom/demo/maps/compose/CameraPositionState;",
        "Lcom/demo/maps/compose/MarkerState;",
        "Lcom/demo/maps/compose/MapProperties;",
        "Lcom/demo/maps/compose/MapUiSettings;",
        "Lcom/demo/maps/compose/MapType;",
        "Lcom/demo/maps/compose/CameraMoveStartedReason;",
        "Lcom/demo/maps/model/LatLng;",
        "Lcom/demo/maps/model/LatLngBounds;",
        "Lcom/demo/maps/model/CameraPosition;",
        "Lcom/demo/maps/model/MapStyleOptions;",
        "Lcom/demo/maps/model/BitmapDescriptor;",
        "Lcom/demo/maps/model/BitmapDescriptorFactory;",
        "Lcom/demo/maps/model/Marker;",
        "Lcom/demo/maps/model/Polygon;",
        "Lcom/demo/maps/model/Polyline;",
        "Lcom/demo/maps/model/Circle;",
    }
    if provider == "google":
        unexpected = sorted(compatibility_types & classes)
        if unexpected:
            raise SystemExit(f"{apk}: Google aliases were replaced by custom runtime classes: {unexpected}")
        native_types = {
            "Lcom/google/maps/android/compose/CameraPositionState;",
            "Lcom/google/maps/android/compose/MarkerState;",
            "Lcom/google/android/gms/maps/model/LatLng;",
        }
        if not native_types <= classes:
            raise SystemExit(f"{apk}: missing native Google classes: {sorted(native_types - classes)}")
    elif not compatibility_types <= classes:
        raise SystemExit(f"{apk}: missing Huawei compatibility classes: {sorted(compatibility_types - classes)}")
    print(f"PASS {apk}: {provider} types/Compose API present; no opposite-provider map/location classes ({len(classes)} classes checked)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--google", type=Path)
    parser.add_argument("--huawei", type=Path)
    args = parser.parse_args()
    if not (args.google or args.huawei):
        parser.error("Provide at least one --google or --huawei APK")
    if args.google: verify(args.google, "google")
    if args.huawei: verify(args.huawei, "huawei")


if __name__ == "__main__":
    main()
