# Map validation — production helpers extension

Validated November 14, 2026 on `flavor-aliases`, based on `0e8e768`.
The implementation remains uncommitted. This record supersedes the earlier
structural-cleanup results; it describes the current helper/clustering extension.

## Results

| Check | Result |
| --- | --- |
| Google unit suite | **5 passed**, no failures/skips |
| Huawei unit suite | **26 passed**, no failures/skips |
| Shared Python migration/exact-screen comparison checks | **9 passed** |
| Google debug and release APK builds | Passed |
| Huawei debug and release APK builds | Passed |
| Both debug test APK builds | Passed |
| Exact dependency versions and provider isolation, all four variants | Passed |
| Packaged DEX isolation, debug/release for both providers | Passed |
| Google native/UI suite, Pixel_4_API_35 emulator, API 35 | **20 passed**, no skipped tests |
| Huawei native/UI suite, HOP-LX9 phone, API 36 | **17 passed**, no skipped tests |
| Supplied screen and three reference files vs `0e8e768` | Byte-identical |
| Forbidden legacy identifiers in project source/docs | Absent |

The Google baseline resolves Maps Compose 6.5.0, Maps SDK 19.0.0, Maps Utils 3.8.2
and Location 21.3.0. Huawei retains Maps 6.15.1.318 and Location 6.16.0.302. Its mask
fallback alone adds pinned earcut4j 2.2.2. Maps Utils KTX is absent. DEX checks also
reject custom geometry/state/cluster interfaces on Google, require those HMS
implementations on Huawei, and reject the triangulator/ported algorithm in Google.

## What the checks establish

**Grouping:** 60 checked-in membership fingerprints were recorded by the pinned
native Google 3.8.2 algorithm. The same deterministic 1,000-item fixtures run in
both unit suites across viewport resize, fixed-zoom panning, item removal/addition
and integer/fractional zoom boundaries. Additional cases cover empty/one/two/three
items, the size >= 2 renderer rule, distance just below/at/above the 50-dp search
half-span and date-line viewport wrapping. Google aliases are used directly with
the native Java algorithm; Huawei uses the adapted algorithm. Both match the
native membership baseline.

**Native clustering/ownership:** shared native tests click groups, individual
stations and ordinary vehicle markers. They verify callback order, consumed and
unconsumed events, native marker identity after movement, showing/hiding a vehicle
info window, item removal, conditional cluster-layer removal and disposal while a
5,000-item request is pending. The vehicle and map survive layer removal. The
wrapper exposes no new info-window callback API; these checks do not claim every
possible custom info-window implementation has been migrated.

**Real helper behavior:** native tests retain the actual bus and stop helpers'
marker IDs after coordinate changes. Bus tests step all 50 ripple frames, refresh
bearing/tint/icon/mode, check deterministic bitmap geometry and verify zero
repeated bitmap generation for unchanged non-ripple movement. Visibility removal
stops its effect. The stop preserves its station callback and unconsumed return.
Drawable dimension validation is exercised. Neutral artwork substitutes for the
unavailable production resources.

**Boundaries:** repeated provider construction returns the same cached parsed
object. All synthetic region vertices are contained by the builder-produced
bounds. Malformed structure, wrong geometry, missing/empty region and out-of-range
coordinates fail explicitly. These are synchronous-load/cache tests using a
synthetic asset, not a measurement or validation of the unavailable real GeoJSON.

**Mask:** the original native HMS world mask failed the exterior-fill pixel test.
Normalizing rings, reversing hole winding and supplying complete holes/style at
native creation did not fix it. Separate triangulated native faces fixed fill but
the stricter test exposed translucent antialias seams. Shared mesh edges are now
merged within each <=90-degree longitude band before native rendering.

The final shared native pixel test passes on both providers: rectangular clear
interior, concave hole/notch, 50% exterior alpha, no stacked-alpha seams along its
exterior scanline, only the two original outline crossings, click identity across
pieces/geometry changes, zoom 12 -> 13, z-order against another native polygon,
and grouped disposal. Pure geometry tests check fill area, band widths, winding
and closure. These checks use synthetic Riyadh-area fixtures; they do not establish
all possible world geometries, global views or real-boundary capacity.

The final readiness checks also apply initial HMS properties/padding before a
queued camera command attaches, and avoid reapplying identical padding. Callback
assertions wait for the completed UI dispatch; map-loaded is not treated as
camera-idle when a state is reattached.

**Regression coverage:** the existing camera pre-map commands/completion,
interruption/disposal/state reuse, marker pixels, multiple maps/styles, picker
behavior and live-ride navigation/recreation tests remain. Google retains native
Parcelable/state/saver interoperability checks. The comparison screen's startup,
route framing, bottom panel/padding and theme controls continue through the real
preserved screen. The body/queue logic and its reference comparison are unchanged.

The manual live-screen pass also observed the real bus bitmap/tracker, live
movement and bottom-panel placement on both devices. Google hybrid tiles rendered;
Huawei hybrid remained blank. Huawei's
[Android SDK reference](https://developer.huawei.com/consumer/en/doc/HMSCore-References/huaweimap-0000001050151757/)
identifies hybrid and satellite modes as unsupported. They are now checked through
`MapType.isSupported()` and explicitly rejected by HMS MapProperties, with the demo
choice disabled. These modes are not claimed as HMS parity coverage.

## Synthetic load profile

Each provider runs two five-second measured windows after paced warmup, with
200 then 1,000 **input stations clustered at zoom 13**, plus 20 moving vehicles.
Four vehicles change bearing at a requested 20 Hz; sixteen move with unchanged
non-ripple visuals. Station count is not a count of independent rendered pins.
The native suites' test frame clock is advanced at wall-clock pace during these
measurements. This is an illustrative instrumented debug workload, not a release
benchmark or production capacity claim.

| Provider / environment | Stations | Java allocations during window | Bitmap frames / icon requests | Marker creations after warmup |
| --- | ---: | ---: | ---: | ---: |
| Google / emulator | 200 | 32,587,872 bytes | 392 / 392 | 0 |
| Google / emulator | 1,000 | 32,755,808 bytes | 392 / 392 | 0 |
| Huawei / phone | 200 | 256,359,752 bytes | 376 / 376 | 0 |
| Huawei / phone | 1,000 | 252,444,640 bytes | 368 / 368 | 0 |

The sixteen static-looking moving vehicles produce no new icons. Only the four
rotating vehicles generate bitmap frames. No vehicle or cluster marker is
recreated in these steady measurement windows.

| Provider | Stations | Timing source | Samples | Median | 95th percentile |
| --- | ---: | --- | ---: | ---: | ---: |
| Google | 200 | Window total frame duration | 111 frames | 17.43 ms | 18.94 ms |
| Google | 1,000 | Window total frame duration | 109 frames | 17.46 ms | 18.89 ms |
| Huawei | 200 | Native SurfaceFlinger presentation intervals | 186 presentations | 16.64 ms | 49.91 ms |
| Huawei | 1,000 | Native SurfaceFlinger presentation intervals | 186 presentations | 16.64 ms | 49.91 ms |

**These timing columns measure different things and cannot rank the providers.**
The phone's HMS SurfaceView produces no Window.FrameMetrics samples. Its actual
native surface presentation timestamps are therefore collected instead; a
presentation interval is not CPU/GPU frame execution duration. The Google
emulator exposes window frame durations but no corresponding map SurfaceView
samples to this collector. Hardware, refresh behavior and instrumentation differ.

`art.gc.bytes-allocated` is cumulative Java allocation traffic for the process,
including SDK, probe and test work. It is not retained heap growth and excludes a
complete native/GPU allocation accounting. HMS shows substantial allocation
traffic with these animated bitmap inputs; production device/load profiling is
required before accepting an animated fleet size. Reusing mutable bitmaps that
native code may still reference was deliberately not used to reduce that number.

`iconRequests` counts native HMS icon-setting/creation requests and Google icon
submissions through the official Compose wrapper/custom cluster renderer. These
are observable SDK requests, **not measured GPU texture uploads**; SDK caching
can change the number of actual uploads. No claim of direct GPU upload counts or
unknown production capacity is made.

## Commands and evidence

```sh
./gradlew :app:testGoogleDebugUnitTest :app:testHuaweiDebugUnitTest \
  :app:assembleGoogleDebug :app:assembleHuaweiDebug \
  :app:assembleGoogleRelease :app:assembleHuaweiRelease \
  :app:assembleGoogleDebugAndroidTest :app:assembleHuaweiDebugAndroidTest \
  :app:verifyMapIsolation --console=plain
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/map_inventory.py --check-shared
python3 scripts/verify_map_apks.py \
  --google app/build/outputs/apk/google/debug/app-google-debug.apk \
  --huawei app/build/outputs/apk/huawei/debug/app-huawei-debug.apk
# Repeat the DEX check for the release APKs.
```

Use an explicit device for native suites:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedGoogleDebugAndroidTest
ANDROID_SERIAL=YOUR_HUAWEI_SERIAL ./gradlew :app:connectedHuaweiDebugAndroidTest
```

The configured test-only `MapTestRunner` lets test activities display above a
keyguard via Android's normal show-when-locked API; it does not unlock the phone
or change production activity behavior. The phone was also unlocked for the final
suites. Its original stay-awake setting is restored after testing. The surface
profile collector requires API 31+; the two tested devices run API 35/36.

Current local logs:

- `/tmp/maps-production-helpers-validation.log`: final unit/build/dependency matrix.
- `/tmp/maps-helpers-final-google.log`: full 20-test Google suite and raw profile.
- `/tmp/maps-helpers-final-huawei.log`: full 17-test HMS suite and raw profile.
- `/tmp/maps-hms-mask-baseline.log` and `/tmp/maps-hms-native-holes-tests.log`:
  failing native mask reproduction/initialization-only attempts.
- `/tmp/maps-final-mask-identity-google.log` and `/tmp/maps-final-mask-identity-huawei.log`:
  strengthened second-click identity check after geometry/zoom changes, passed on both.
- Unit XML/HTML: `app/build/test-results/` and `app/build/reports/tests/`.
- Native screenshots/profiles copied locally under `app/build/outputs/map-validation/`.

Logs and generated artifacts are local evidence, not source files required by a
fresh checkout. The unit membership baseline is checked in under
`app/src/test/resources/cluster-membership-3.8.2.txt`. CI builds both variants,
runs unit/Python contracts and inspects dependencies/DEX; device suites require
separate native execution.

## Production release gates

The full 120-file app, its module graph and SDK-bound Java/binary dependencies are
not supplied. Remaining helper implementations, the original artwork and real
GeoJSON are unavailable. LiveTrackingMap theme/queue/stale-vehicle logic is outside
this extension and remains untouched. The demo still has shrinking disabled;
production R8/configuration validation is required. The connected phone includes
microG components: verified SDK/dependency/APK isolation does not establish a
GMS/microG-free runtime. That separate environment remains required.

The current HMS scene already rejects invalid style JSON by checking setMapStyle's
result. No new claim of adding that preexisting validation is made. See
[implementation details](details.md) for the corrected audit and supported scope.
