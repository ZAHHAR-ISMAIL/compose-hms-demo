# Google-shaped maps on Google and Huawei flavors

Read [the architecture walkthrough](map-architecture-explained.md) first for a plain
explanation of the shared files, type/state ownership, execution paths, Google-side
impact, and alternative designs. This document is the API/migration reference.

The shared Kotlin/Compose API is `com.demo.maps`. Both flavors compile the same
screen code. Google delegates to the official Maps Compose 8.2.2 library; Huawei
uses Map Kit 6.15.1.318 with an independent map subcomposition and retained overlays.
This implements the API used by the supplied bus-map example, not the entire
Google Maps SDK. The original application's other 120+ files are not in this repo.

## One app module, two flavors

| Source set | Responsibility | Native dependencies |
| --- | --- | --- |
| `app/src/main` | Screens, models, camera/marker state, Compose facade, backend contract | No native SDK types in shared code |
| `app/src/google` | Google conversions and official Compose forwarding | Google Maps Compose |
| `app/src/huawei` | HMS lifecycle, camera callbacks, Compose nodes, marker bitmaps | HMS Map Kit |

The app keeps the existing `store` flavor dimension. `googleImplementation` and
`huaweiImplementation` include the respective native SDK directly. Settings includes
only `:app`. The bridge is ordinary Kotlin source in the existing app; no new library
modules, module build files, or feature-module variant configuration are required.

## Compare the exact supplied screen

- [Reference source with neutral demo names](reference/bus-on-demand/original.kt.txt)
- [Migrated source, without demo compile context](reference/bus-on-demand/migrated.kt.txt)
- [Exact migration diff](reference/bus-on-demand/imports-only.diff)
- Runnable file: `app/src/main/java/com/demo/hmscomposeapp/busondemand/BusOnDemandRideMap.kt`

The comparison changes exactly **12 map imports and one qualified initializer
reference**. The complete function bodies, including the transport queue, two-second
segments, `delay(16)`, camera effects, mask polygon, pinned marker composition,
`LiveBusTransportMarker` call, and bearing calculation, are otherwise identical.
The same file is compiled into both Google and Huawei builds.

The pasted snippet omitted package/non-map imports and its external dependencies.
The runnable file adds the missing compile context above a marked source boundary.
`BusOnDemandDemoSupport.kt` supplies clearly labeled stand-ins for the unavailable
transport model, theme, icon helper, map settings, marker helper, and logger.
Both comparison copies use neutral `DemoTheme`/`DemoIcon` names and local app
drawables. This naming cleanup applies equally to both copies, separately from the
13 map-reference changes; no external application namespace or resource aliases
are required. Production should keep its real helpers/assets and migrate their
map imports; it does not need these stand-ins.

`LiveRideDemoScreen` only surrounds that actual screen with controls and sample
transport queues. Its observer exposes readiness and camera control to the demo
without changing the pasted function. The Python comparison tests enforce this
separation and fail if additional screen body changes are introduced.

## App wiring

Flavor-specific `createMapBackend()` selects the backend once. The activity wraps
the shared screen in:

```kotlin
ProvideMapBackend(createMapBackend()) {
    MapDemo()
}
```

The optional `MapsInitializer.initialize(context)` compatibility entry point works
inside that root. Backend initialization uses the application context. Provider
selection is a build decision; microG/GMS detection does not affect the map path.
Location continues through the existing, separate flavor-specific LocationProvider.

## Supported source contract

| Google API family | Shared replacement | Implemented behavior |
| --- | --- | --- |
| `maps.model.LatLng`, `LatLngBounds`, `CameraPosition` | `com.demo.maps.model` | Coordinate normalization, bounds including antimeridian, camera model |
| `maps.CameraUpdateFactory`, `CameraUpdate` | `com.demo.maps` | Target, target/zoom, bounds, full position, scroll, relative/absolute zoom |
| `CameraPositionState`, `rememberCameraPositionState` | `com.demo.maps.compose` | Position, moving/reason state, move, suspending/cancellable animate, scalar saver |
| `MarkerState`, remembered marker state helpers | `com.demo.maps.compose` | Position updates, dragging, info-window control, scalar saver |
| `GoogleMap`, `MapProperties`, `MapUiSettings`, `MapType` | `com.demo.maps.compose` | Map properties, UI controls, gestures, padding, loaded/tap/location callbacks |
| `Marker`, `MarkerComposable` | `com.demo.maps.compose` | Identity, bitmap/resource icons, Compose icons, rotation, anchor, z-index, drag/click/info-window callbacks |
| `Polygon` | `com.demo.maps.compose` | Points, holes, color, stroke width, geodesic, click, tag, visibility, z-index |
| `Polyline`, `Circle` | `com.demo.maps.compose` | Geometry, colors/width/radius, geodesic for lines, click/tag/visibility/z-index |
| `MapStyleOptions`, bitmap descriptors | `com.demo.maps.model` | Raw resource styles; bitmap, resource and default marker icons |

For an existing screen, replace imports; retain calls such as:

```kotlin
cameraPositionState.animate(
    update = CameraUpdateFactory.newLatLngZoom(fromCoord, 15f)
)

GoogleMap(
    properties = properties,
    cameraPositionState = cameraPositionState,
    contentPadding = PaddingValues(bottom = bottomContentHeight)
) {
    Polygon(points = worldBoundaries, holes = listOf(riyadhGeo))
    MarkerComposable(state = markerState, rotation = bearing) {
        ExistingBusIcon()
    }
}
```

`BusOnDemandRideMap` is the runnable complex example. Its map imports all resolve to
the shared API. On Huawei, that API calls HMS MapView and HMS overlays; the screen
does not need Huawei-specific branches or a second implementation.

## State, lifecycle and animation rules

- Call map/state commands on the main thread. Each camera state belongs to one map;
  each marker state belongs to one marker. Both can be reused after disposal.
- Camera commands can be constructed before SDK initialization. The last move
  before map layout wins. Animations suspend until readiness; movement, replacement
  animation, caller cancellation, and disposal cancel the previous animation.
- HMS initial movement waits for a laid-out native map; HMS animation additionally
  waits for the first native loaded frame, preventing an SDK startup cancellation.
  Google readiness uses the underlying map and layout, independently of tile loading.
- HMS forwards create/start/resume/pause/stop/destroy/low-memory and native saved
  state. Native map destruction disposes overlays, listeners, bitmap views and
  pending camera work first. Multiple map instances have separate save-state keys.
- Marker position and rotation update the existing native object. Static Compose
  icons are retained; visual keys/content invalidation refresh their bitmap. The
  native instrumentation test checks actual pixels and stable native marker IDs.
- Compose marker content is a bitmap, not an interactive UI subtree. Put interaction
  in marker callbacks. For continuous cross-provider ripples, animate a map circle
  rather than regenerating a complex icon for every movement frame.
- Camera/marker savers store primitive coordinate values, not SDK Parcelables.
  Shared LatLng is not an SDK Parcelable. Existing persisted SDK objects, navigation
  arguments and serialization adapters need explicit scalar migration.

## Migrate the larger app

1. Audit the real source tree before changing it:

   ```sh
   python3 scripts/map_inventory.py /absolute/path/to/production-app --json > map-inventory.json
   ```

   Review Compose/native calls, utilities, places, location, web services, XML views,
   Java files, and native escape hatches. Confirm the app's API versions against
   this bridge's pinned versions rather than upgrading SDKs during migration.

2. Add the shared bridge to main sources and native implementations to the existing
   flavor source sets, then configure flavor-only SDK dependencies, the root provider,
   credentials, and flavor-specific style resources. Additional modules are optional.

3. Migrate shared map/marker helpers first. In this repo the replacement namespace
   is `com.demo.maps`; it can be renamed consistently to the production namespace.
   Run the controlled migration on feature sources, not provider implementations:

   ```sh
   python3 scripts/migrate_map_imports.py /absolute/path/to/feature/src
   python3 scripts/migrate_map_imports.py /absolute/path/to/feature/src --apply
   ```

   The default is a diff-only dry run. Rewriting covers explicit Kotlin imports,
   aliases and fully qualified code references. Comments and string literals are
   preserved. Unsupported references and wildcard imports stop the whole operation
   before any writes. It is a source-name migration, not an API-compatibility proof;
   compile both flavors after each batch.

4. Validate a representative complex screen in Google, then Huawei. Only then
   migrate the remaining screens in batches. Record every body/signature change
   with its SDK-specific reason. Keep unrelated existing screen defects separate.

5. Move unsupported native behavior into small flavor helpers. Do not duplicate
   entire screen implementations to handle a single provider-specific operation.

### Explicit limits

The public API deliberately has no SDK-shaped `GoogleMap` object, raw `MapView`,
fragments, `MapEffect`, projection, `GoogleMapOptions`, Street View, advanced markers,
clustering, tile/ground overlays, line cap/pattern types, or composable info windows.
These usages require additional adapters and fail compilation until implemented;
they are not silently ignored. Java, XML, reflection and precompiled libraries
that expose Google classes cannot be made compatible by Kotlin import replacement.

The supplied `LiveBusTransportMarker` and `defaultMapSetting()` implementations
were not provided. Audit them against the supported contract before migrating the
original screen. Search/geocoding/directions and external route data are separate
services and are not automatically redirected by changing the map renderer.

The original sample has remembered properties and marker state without update keys;
these have deliberately been preserved. The surrounding harness recreates only the
demo instance when its theme/map-type controls change. It initially feeds a stationary
bus sample, then a separate batch, allowing the original camera effect to finish.
These are harness choices, not edits to or fixes for the supplied screen logic.

## Verification

See [the recorded device/build results and remaining validation limits](map-validation.md).

```sh
./gradlew :app:testGoogleDebugUnitTest :app:testHuaweiDebugUnitTest
./gradlew :app:assembleGoogleDebug :app:assembleHuaweiDebug :app:assembleGoogleRelease :app:assembleHuaweiRelease
./gradlew :app:verifyMapIsolation
python3 scripts/map_inventory.py --check-shared
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify_map_apks.py --google app/build/outputs/apk/google/debug/app-google-debug.apk --huawei app/build/outputs/apk/huawei/debug/app-huawei-debug.apk
```

Run native suites against an explicit target to avoid testing the wrong device:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGoogleDebugAndroidTest
ANDROID_SERIAL=YOUR_HUAWEI_SERIAL ./gradlew :app:connectedHuaweiDebugAndroidTest
```

The shared native suite checks pre-readiness camera animation and completion,
interruption on disposal and state reuse, marker image refresh and native identity,
conditional overlay removal, multiple maps, and style switching. Existing tests
cover picker confirmation/cancellation, timeout and scalar state restoration.

The CI workflow builds debug/release for both flavors, runs unit contracts and
migration tests, checks dependency isolation, and parses DEX class definitions to
reject opposite-provider map/location classes. Native device tests require real
device/emulator execution and are separate from that build workflow. This demo's
existing release configuration has shrinking disabled; R8 validation belongs to
the production application's shrinking configuration.

A Huawei device containing microG is not a clean GMS-free test environment. Verify
the selected backend and APK isolation, then run against a separate GMS-unavailable
test environment. Do not use the device manufacturer alone as evidence.

## Sources and notices

- [Google Maps Compose](https://github.com/googlemaps/android-maps-compose): official
  Google renderer and reference for map subcomposition/state; Apache-2.0 notices in
  `docs/licenses/maps-compose/NOTICE` and `docs/licenses/maps-compose/LICENSE`.
- [Android build variants](https://developer.android.com/build/build-variants):
  flavor source sets and dependency isolation. Resources can override by flavor;
  Kotlin/Java definitions in main and a flavor cannot duplicate the same class.
- [Huawei map conversion guide](https://developer.huawei.com/consumer/en/doc/Tools-Guides/map-conversion-0000001050062227/):
  native API differences and incompatible style JSON formats. The same raw resource
  names have separate Google/Huawei contents in this app.
