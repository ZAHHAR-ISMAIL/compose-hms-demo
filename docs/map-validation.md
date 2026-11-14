# Map compatibility validation

## Direct flavor cleanup — November 14, 2026

The current working tree uses direct flavor entry points. Shared screens remain
in main; `main/java/com/demo/maps` has been removed completely. Each flavor now
owns `MapsInitializer` and `compose/MapsCompose.kt`. Google forwards directly to
official Compose. HMS view, lifecycle, overlay, camera and bitmap internals live
under its own `compose/internal` package.

The application initializes its selected SDK directly before composing screens.
There is no runtime map implementation registration or root provider wrapper.
The launcher's small test component is named `DemoMapRenderer`; the location
interface has its own `LocationProvider.kt`. The optional comparison observer only
observes camera/loading and does not select an implementation.

Both flavors' six public map/marker/shape signatures and defaults match the earlier
shared entry points (Google's Compose target annotations are preserved separately).
The supplied screen and reference/diff files remain byte-identical to `369c8be`.
No further screen import changes are required. Changes remain uncommitted.

The four APK builds, both test APK builds, unit suites (Google 1, Huawei 20), all
six Python tests, dependency isolation and debug/release DEX isolation pass. The
updated Google emulator suite passes all 13 tests, and the Huawei phone suite
passes all 10 tests. The first phone attempt was obstructed by its lock screen;
the full suite passed after unlocking and temporarily keeping it awake over USB.

Current run logs: `/tmp/maps-direct-flavors-build.log`,
`/tmp/maps-direct-flavors-google-tests.log` and
`/tmp/maps-direct-flavors-huawei-tests.log`. The older results below describe prior
revisions and must not be read as evidence that the removed routing is still present.

## Earlier native Google alias revision — November 14, 2026

Implemented on `codex/maps-native-aliases`, based on `369c8be` from
`kotlin-DSL-flavor`. This is still one `:app` module with the existing store flavors
and pinned SDK versions. Google uses native aliases and direct official Compose
forwarding. The previous custom models/state/controllers now compile only on Huawei.

| Check | Result |
| --- | --- |
| Google app unit test | 1 passed |
| Huawei custom geometry/state contracts plus app unit test | 20 passed |
| Python migration and exact-screen comparison tests | 6 passed |
| Google debug and release APK builds | Passed |
| Huawei debug and release APK builds | Passed |
| Both instrumentation APK builds | Passed |
| Runtime dependency isolation, all four variants | Passed |
| Packaged DEX isolation, debug/release for both providers | Passed |
| Google native/UI tests, Pixel_4_API_35 emulator, Android 15 / API 35 | 13 passed, 0 skipped |
| Huawei native/UI tests, connected HOP-LX9, Android 16 / API 36 | 10 passed, 0 skipped |

The Google test suite includes three new integration scenarios: native model,
factory and saver identity with a Parcelable round trip; a forwarded map controlled
through the original native state with native marker callback handles; and native
camera/marker restoration through the forwarded remember factories. Shared renderer
tests still cover camera commands before map creation, animation completion,
disposal/reuse, actual custom marker pixels and retained native IDs, conditional
removal, polygon holes, multiple maps and style switching. Both complete UI suites
include recreation and navigation of the actual preserved BusOnDemandRideMap.

Shared tests explicitly initialize the SDK before constructing camera commands.
Google factories are now native; the pre-map test is not a pre-SDK-initialization test.

Huawei's 19 custom implementation tests moved from shared tests to `testHuawei`;
they were not discarded. Google is validated against its official state/model
behavior rather than being required to satisfy custom HMS internals. DEX checks
also reject custom map model/state/controller classes in the Google APK and require
the Huawei-compatible implementations in its APK.

The runnable supplied screen and all three reference/diff files are byte-identical
to the base commit. The exact comparison still changes only 12 map imports and one
qualified initializer reference. A code-token comparison confirms the four moved
model/state/command files and all five HMS integration files retain their original
implementation; accompanying comments were updated for their new flavor ownership.

A manual visual pass also opened the preserved live-ride screen in both providers,
observed the custom bus marker moving and changing bearing, and switched day/night
styles. Screenshots show the map-ready panel above the bottom controls; a wider
Google zoom shows the dark world mask surrounding its region hole. Local captures
are generated under `app/build/outputs/map-validation/native-aliases-*.png`. These
are visual smoke checks, not a pixel-parity or performance benchmark.

Build and test logs for this run are available locally in
`/tmp/maps-native-aliases-build.log`, `/tmp/maps-native-aliases-google-tests.log` and
`/tmp/maps-native-aliases-huawei-tests.log`. Generated JUnit XML and reports live under
`app/build/test-results`, `app/build/outputs/androidTest-results` and
`app/build/reports`. The full architecture and source layout are explained in
[the updated guide](map-architecture-explained.md).

**Observed visual limitation:** at a wider zoom the Huawei screen draws the region
boundary, but its outside world-mask fill does not visibly darken the basemap as
it does on Google. See the local `native-aliases-huawei-mask.png` and
`native-aliases-google-mask.png` captures. Huawei's overlay implementation and
screen inputs are unchanged from the base commit; large-world polygon rendering
needs separate investigation. The passing polygon/overlay scenarios do not prove
pixel-equivalent masking. This refactor preserves HMS behavior rather than fixing
that existing implementation's rendering differences.

These results do not establish a GMS-free runtime environment: the connected phone
contains microG. See the limitations below. Production shrinking remains disabled
in this demo, and the original 120-file application has not been audited.

## Previous common-model adapter revision — November 12, 2026

The bridge now lives entirely in `:app` source sets (`main`, `google`, `huawei`).
Demo names and local drawable references are neutralized consistently in both
comparison copies and the runnable file; external resource aliases were removed.
The runnable demo invokes the exact supplied `BusOnDemandRideMap` body. The original,
migrated copy, and the 13-line migration diff are in `docs/reference/bus-on-demand`.
Two comparison tests enforce the 12 import changes and the single initializer
reference change, and verify that this is the source actually compiled into the app.

Both flavor unit suites pass: **20 tests per flavor** (the 19 relocated bridge
contracts plus the existing app test). All **6 Python migration/comparison tests**
pass. Both debug/release builds, both app lint variants, and dependency/APK isolation
pass. The unchanged **10-test Google emulator suite passes**, including navigation,
style changes and activity recreation of the actual preserved BusOnDemandRideMap.
The Huawei instrumentation APK also compiles in the new single-module layout.

No physical Huawei device was connected for this revision. The earlier Huawei
results below validate the same native bridge before its source relocation; they
must not be interpreted as a device test of the newly preserved pasted screen.

## Earlier bridge validation — November 11, 2026

| Check | Result |
| --- | --- |
| Shared geometry/camera/marker contract tests | 19 passed |
| Controlled migration tool tests | 4 passed |
| Existing app unit tests | 1 passed per flavor |
| Huawei native/UI suite, HOP-LX9, Android 16 / API 36 | 10 passed |
| Google native/UI suite, Pixel 4 emulator, Android 15 / API 35 | 10 passed |
| Google/Huawei debug and release builds | Passed |
| Earlier library layout's Android lint | Passed; SDK version-update warnings remain intentional |
| Shared-source and runtime dependency isolation | Passed |
| Debug/release APK DEX class isolation | Passed |

Native tests validate camera commands before readiness, completed animations,
cancellation on map disposal, camera-state reuse, custom Compose-marker pixels,
marker identity after movement, conditional marker removal, multiple maps,
flavor-specific styles, and the complete live-ride screen across real activity
recreation. Existing picker tests exercise confirmation, cancellation, location
timeout, and saved state.

The Huawei tests caught a startup cancellation: Map Kit could abandon an animation
submitted before its first rendered frame. HMS animation now waits for the native
loaded callback; initial move commands still apply once the view is laid out.

## GMS-free runtime verification remains incomplete

The physical Huawei device contains microG. Its passing tests establish that the
HMS renderer works there, not that the device is a clean GMS-free environment.

An additional experiment disabled Google Play Services on the emulator. The HMS
SDK could not obtain a map delegate (`not load map hmsState = 1`), so four native
renderer tests timed out. Installing the physical device's HMS Core APK into the
emulator did not make that environment usable. This experiment does not establish
GMS-free runtime compatibility. Google Play Services was restored, the temporary
HMS Core emulator install was removed, and the final Google suite passed afterward.

APK inspection independently proves that the Huawei APK defines no Google Maps,
Maps Compose, or Google fused-location classes; dependency graphs also reject those
SDKs. A genuine HMS-only supported device is still required to close the remaining
runtime acceptance criterion. The Huawei device's system services were not disabled.

## Production follow-up

The original 120+ source files and the implementations of `LiveBusTransportMarker`
and `defaultMapSetting()` were not supplied. Use the inventory and migration tools
against that application and implement any APIs outside the documented subset.
The demo's release configuration has shrinking disabled; its release build result
does not constitute an R8 test of the production application. Long-duration fleet
load, pixel-by-pixel polygon-mask parity and production network/service failures
also need validation with the original app's assets and workload.
