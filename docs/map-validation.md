# Map compatibility validation

## Current revision — November 12, 2026

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
