# Map flavor demo

A shared Jetpack Compose map demo with a separate location picker.

| Flavor   | Map SDK        | Current location      |
| -------- | -------------- | --------------------- |
| `google` | Google Maps    | Google fused location |
| `huawei` | Huawei Map Kit | HMS fused location    |

Shared UI, coordinates, geocoding and provider interfaces live in `app/src/main`.
Each flavor implements `MapProvider`, `LocationProvider`, and the same factory functions
in its own source set. Google uses the official Maps Compose library. Huawei keeps a
small `AndroidView` host because Map Kit exposes `MapView`; the host owns overlays,
forwards the full lifecycle, saves view state, and releases queued callbacks. No SDK
map, coordinate, or location types cross into shared code.

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
