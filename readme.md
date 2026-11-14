# Map flavor demo

A shared Jetpack Compose map demo with a location picker and a live ride map.
Both flavors use the Google-shaped `com.demo.maps` compatibility API. Google resolves
its models/state to the original SDK types through aliases and forwards to official
Maps Compose; Huawei retains its HMS Compose integration.

**Start with [the architecture walkthrough](docs/map-architecture-explained.md)** for
the folder structure, direct flavor entry points, native Google aliases/forwarding,
Huawei-only state, and the tradeoffs at module/persistence boundaries.

| Flavor   | Map SDK        | Current location      |
| -------- | -------------- | --------------------- |
| `google` | Google Maps    | Google fused location |
| `huawei` | Huawei Map Kit | HMS fused location    |

This is one Gradle module, `:app`. Shared screens live in `app/src/main`; all map
compatibility declarations live in `app/src/google` and `app/src/huawei`. Google
forwards directly to the official library. HMS native rendering details live in
its `compose/internal` folder. There is no shared `main/maps` folder or runtime
map implementation registry. Flavor-specific dependencies include only the selected map
and location SDKs. There are no extra map modules or library build files.

The **Live ride demo** now executes the supplied **BusOnDemandRideMap** screen.
Its queue processing, interpolation, camera effects, polygons, marker UI, and bearing
calculation are preserved. Only 12 map imports and one fully qualified initializer
reference change. Missing non-map imports and demo stand-ins for the unavailable
production helpers/resources are kept outside that comparison.

Compare [the reference with neutral demo names](docs/reference/bus-on-demand/original.kt.txt),
[the migrated code](docs/reference/bus-on-demand/migrated.kt.txt), and
[the exact diff](docs/reference/bus-on-demand/imports-only.diff).
The runnable source is `app/src/main/java/com/demo/hmscomposeapp/busondemand/BusOnDemandRideMap.kt`.
See [the compatibility contract and migration guide](docs/map-compatibility.md) for
supported APIs, limits, an inventory tool, controlled import migration, and testing.

The launcher shows one marker and a fixed, road-following 3.34 km route from Kingdom
Centre to King Fahd Library in Riyadh (not a runtime directions service). Pick location
opens a separate Compose screen. Tap to select, optionally use
current location, then Confirm or Cancel. Confirm updates the launcher marker; the route
stays fixed. Selection and an open picker survive recreation. Address lookup falls back
to coordinates when geocoding is unavailable.

The route was generated with [OSRM](https://project-osrm.org/) from
[OpenStreetMap](https://www.openstreetmap.org/copyright) data and is labeled in the UI.
The camera includes both the route and selected marker, so a selection outside Riyadh
remains visible.

## Build

Use JDK 17 and Android SDK 36 configured in `local.properties`:

```sh
./gradlew :app:assembleGoogleDebug :app:assembleHuaweiDebug
```

APKs:

- `app/build/outputs/apk/google/debug/app-google-debug.apk`
- `app/build/outputs/apk/huawei/debug/app-huawei-debug.apk`

Contract tests and SDK isolation:

```sh
./gradlew :app:testGoogleDebugUnitTest :app:testHuaweiDebugUnitTest :app:verifyMapIsolation
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/map_inventory.py --check-shared
python3 scripts/verify_map_apks.py --google app/build/outputs/apk/google/debug/app-google-debug.apk --huawei app/build/outputs/apk/huawei/debug/app-huawei-debug.apk
```
