# Production helpers and clustering: implementation details

This working tree extends the existing `flavor-aliases` branch. It stays in one
`:app` module, with one shared copy of each screen and no runtime SDK selection.
Changes are intentionally uncommitted. The full production application is not in
this repository.

## What changes in a production screen

For the supported map calls, change imports and qualified initializer references.
Keep the screen's camera calls, nested map declarations, calculations and queues.
The supplied `BusOnDemandRideMap.kt` and all three reference/comparison files are
unchanged by this extension: their established comparison remains **12 imports
and one initializer reference**.

```kotlin
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.GoogleMapComposable
import com.demo.maps.compose.AppClusteringWrapper
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.clustering.Cluster
import com.demo.maps.model.LatLng
```

Clustering is a helper boundary. Its manager, renderer, native effects and input
handlers belong in flavor sources. Callers keep the same parameters and callbacks;
they do not manage a raw map or duplicate the screen. If the production helper has
a different name/package, preserve that existing name/package for the two flavor
implementations when applying this demonstration to the real app.

The migration script translates `GoogleMapComposable`, `ClusterItem` and `Cluster`
imports now that both implementations exist. It still rejects `ClusterManager`,
`DefaultClusterRenderer`, `MapEffect`, `InputHandler`, wildcard imports and other
unimplemented utilities in shared source. An unsupported file stops the entire
`--apply` operation before any writes.

## Dependency baseline

The Google compile graph was resolved and compiled before implementation. Both
compile and runtime graphs are checked again by `verifyMapIsolation`.

| Dependency | Google | Huawei |
| --- | --- | --- |
| Maps Compose | 6.5.0 | Local HMS Compose implementation |
| Map SDK | Google 19.0.0 | HMS 6.15.1.318 (unchanged) |
| Clustering grouping | android-maps-utils 3.8.2 | Bounded adaptation of 3.8.2 |
| Location | Google 21.3.0 | HMS 6.16.0.302 (unchanged) |
| World-mask triangulation | Native Google polygons | earcut4j 2.2.2, only for the bounded fallback |

These versions are pinned. The supplied older Compose 4.3.0 catalog entry does
not select this build. Maps Utils KTX is not used or added. The production alias
provided by the user selects Maps Compose 6.5.0. The demo's Compose UI/toolchain
versions otherwise remain unchanged; production toolchain compatibility is a
separate integration check.

## Why the two flavor implementations differ

Google aliases retain original Google type identity:

```kotlin
// app/src/google
package com.demo.maps.clustering

typealias ClusterItem = com.google.maps.android.clustering.ClusterItem
typealias Cluster<T> = com.google.maps.android.clustering.Cluster<T>
```

Huawei supplies actual Java interfaces with matching getter contracts:

```java
// app/src/huawei; annotations omitted here for brevity
public interface ClusterItem {
    LatLng getPosition();
    String getTitle();
    String getSnippet();
    Float getZIndex();
}
```

A Kotlin production model can keep Java-style overrides such as
`override fun getPosition(): LatLng`. Kotlin property access such as `item.title`
works in both flavors. Google gets the native Java interface; Huawei gets the
compatible Java interface using Huawei's application-owned coordinate type.
Aliases do not emit replacement runtime classes and do not adapt precompiled
Google-dependent binaries or Java imports. Persisted SDK objects, feature-module
boundaries, Java callers and dependencies exposing SDK types still need review.

`GoogleMapComposable` is also flavor-specific: Google aliases the official
annotation; Huawei declares a map composition target and annotates its overlay
entry points. Marker bitmap UI uses the separate ordinary Compose UI target.
This catches accidental UI/map composition mixing during compilation.

## Clustering execution and ownership

```text
Shared screen: AppClusteringWrapper(items, callbacks, markerImageResId)
    google -> native ClusterManager + native 3.8.2 view-based algorithm
              -> retained custom DefaultClusterRenderer subclass
    huawei -> adapted 3.8.2 view-based algorithm + spatial index/projection
              -> retained HMS markers owned by one map-composition node
```

The adaptation includes only grouping/index/projection math and the two data
contracts. It does not reproduce the utility renderer, manager, collections API,
KTX or the full Google SDK. Original Apache-2.0 headers and notices are retained in
[the notices](licenses/maps-utils/NOTICE).

Both implementations:

- Use the actual map viewport **in dp**, including resizing. The supplied helper
  converted the entire screen to physical pixels; correcting that is intentional.
- Recluster after fixed-zoom idle panning, resizing and item changes. They avoid
  regrouping every camera animation frame.
- Preserve the native 3.8.2 threshold: `minClusterSize = 2` means **size >= 2**.
- Use the supplied drawable for groups and individual stations, with animations
  disabled. Item anchors are `(0.5, 0.5)`; group anchors remain `(0.5, 1)`.
- Keep position, title/snippet fallback and z-index metadata, refreshing an open
  native info window when its contents or position change. Item position must
  be immutable while inside an algorithm generation, as required by ClusterItem.
- Retain markers when their logical item/group remains. Position-only vehicle
  updates do not reconstruct cluster markers.
- Compute grouping off the UI thread and apply native marker operations on the
  Android main thread. Replaced or disposed requests cannot publish stale results.

On Google the native manager's uncancellable AsyncTask path is replaced by an
owned coroutine. The custom renderer still subclasses `DefaultClusterRenderer`,
but its disabled-animation path updates retained native collections synchronously.
This avoids the upstream renderer's private Handler/executor resurrecting markers
after disposal. Native manager, native grouping algorithm and native marker
collections remain in use. This is an intentional cleanup adaptation to the
supplied helper, rather than an unmodified copy of that helper.

A tiny Google-only Java `SceneOwnedMarkerManager` overrides the utility's
package-private deferred listener-installation hook. Its package is necessary to
override that hook; it replaces no SDK class. Official Maps Compose keeps listener
ownership and receives cluster events through its native `InputHandler`.

On Huawei, `HuaweiClusterNode` participates in the existing scene-owned listener.
There is no second `map.setOnMarkerClickListener` competing with vehicles. Nodes
are visited in composition order. A `true` return stops dispatch; `false` continues.
Within the wrapper, the group/item callback runs first. If unconsumed, the external
marker callback runs next. Ordinary vehicle nodes can then receive the same event.
This preserves the supplied wrapper's Boolean behavior rather than treating
ownership as automatic consumption. Native default info windows remain available
for titled items. The wrapper exposes no new info-window callback contract.

Initial HMS map options and viewport padding are applied before queued camera
commands attach. Unchanged padding/description is not reapplied on recomposition,
avoiding native camera reframing during an animation. Map-loaded and camera-idle
remain separate observations; tests await the relevant event.

Removing a layer cancels its job and removes only its markers and callbacks.
Removing a map disposes all its nodes before destroying MapView. Multiple map
instances have independent ownership and listener lists.

## Real bus marker and stop helpers

[LiveBusTransportMarker](../app/src/main/java/com/demo/hmscomposeapp/busondemand/LiveBusTransportMarker.kt)
now uses the supplied Canvas implementation rather than the earlier simplified
circle/icon stand-in. The public parameters stay the same.

Its fixes are deliberately inside the helper:

- `rememberUpdatedMarkerState(markerPosition)` keeps one native marker per
  vehicle while its position changes.
- The stopped ripple has 50 frames, 50 ms per frame: **20 FPS and 2.5 seconds**.
  Its two radii, stroke widths/fading, three-base-width canvas, centered anchor,
  tracker placement, zero-bearing offset and mode offset match the supplied code.
- A non-ripple image is cached until icon, tint, bearing or mode changes. Moving a
  vehicle with otherwise identical visuals produces no new bitmap/descriptor.
- Icon, tint and mode changes invalidate the correct inputs. Internal timing can
  be stepped by tests without changing the public helper signature.
- Published bitmaps are never overwritten or recycled while native code could
  retain them. HMS Compose marker content follows the same ownership rule.

The tracker and vehicle artwork are neutral demo substitutions, clearly labelled
in the demo. They are not pixel-identical copies of unavailable production art.
The comparison harness can pause its internal ripple clock, without editing the
supplied screen body or queue. Actual frame scheduling is subject to device load;
20 FPS is the requested cadence, not a guaranteed delivery rate.

[BusStopMarker](../app/src/main/java/com/demo/hmscomposeapp/map/BusStopMarker.kt)
retains the `title`, `latPosition`, `visible`, `onStationClick` contract, center
anchor and `false` click return. Its state now follows coordinate changes.
[bitmapDescriptorFromVector](../app/src/main/java/com/demo/hmscomposeapp/map/MarkerBitmaps.kt)
validates positive drawable dimensions before allocation. Production should retain
its artwork/resource namespace and migrate map imports in these helpers.

## Boundary loader and unavailable real data

[RegionBoundsLocalProvider](../app/src/main/java/com/demo/hmscomposeapp/map/RegionBoundsLocalProvider.kt)
keeps the synchronous Context constructor and getter names. It caches the parsed
result across provider instances, keyed by application context and resolved asset
filename. Failed parsing is not cached. Shared immutable lists avoid accidental
mutation of a cached result. Returned bounds use `LatLngBounds.builder()`.

Parsing checks FeatureCollection/Polygon geometry, nonempty features, both required
rings, valid coordinate pairs/ranges and at least three distinct vertices. The
first load remains synchronous; no screen-loading/coroutine contract was introduced.
The demo uses Android's JSON parser instead of adding Gson solely for this loader.
Valid supplied coordinate order/results are preserved. Production can retain its
DI annotations and resource wiring; this demonstration has no Hilt configuration.

The actual production GeoJSON was unavailable. The checked-in
`synthetic_region_boundaries.json` is explicitly synthetic and cannot establish
production geometry correctness, loading cost or mask capacity. The inferred
`MapBoundaries`/`RegionBoundsProvider` contracts should be checked against the
actual production declarations before integration.

## Unsupported map types are explicit

A final manual smoke check found a blank HMS hybrid basemap. Huawei's
[Android HuaweiMap reference](https://developer.huawei.com/consumer/en/doc/HMSCore-References/huaweimap-0000001050151757/)
marks both hybrid and satellite modes as unsupported, despite declaring their
constants. The bridge now exposes `MapType.isSupported()` in both flavors. Google
retains all its native modes. Huawei supports NONE/NORMAL/TERRAIN; constructing
Huawei MapProperties for HYBRID/SATELLITE fails with a clear exception instead of
silently rendering blank tiles.

The surrounding demo disables its hybrid choice on HMS and labels it unavailable.
This is a real SDK-specific limitation to handle in production map-type selection;
the supplied map screen body is still unchanged. No satellite provider replacement
or HMS SDK upgrade was introduced.

## HMS world-mask fix

The native pixel test first reproduced the missing exterior fill with the existing
six-point world envelope and a synthetic rectangular hole. Initializing all holes
and styles at polygon creation, normalizing rings and reversing hole winding did
not fix that renderer failure on the HOP-LX9.

The fallback is therefore Huawei-only, and limited to a non-geodesic rectangular
world envelope with holes. Ordinary polygons keep native HMS polygon updates;
Google keeps official native polygon rendering.

1. Project outer/hole rings to Web Mercator.
2. Triangulate outer-minus-holes using pinned earcut4j 2.2.2.
3. Clip each triangle into longitude bands no wider than 90 degrees.
4. Merge shared mesh edges within each band into connected native faces (including
   bounded holes), avoiding translucent antialias seams between separate triangles.
   Render those non-overlapping faces with no internal stroke.
5. Render only original outer/hole outlines as separate native lines.
6. Route clicks and tags through one logical identity; dispose every owned piece
   and outline together.

Geometry is recomputed when geometry changes, not for color/visibility updates.
Area validation rejects incomplete triangulation rather than silently publishing
a partial mask. No screen-level dark layer is used. Synthetic tests cover both
rectangular and concave holes. A dense real boundary can produce many native fill
pieces; its cost cannot be accepted without the missing production asset.

**Audit correction:** the existing HMS scene already checks the Boolean result of
`setMapStyle` and rejects invalid style JSON. The earlier audit's contrary statement
was wrong. This extension does not claim to add that preexisting validation.

## What has not been changed or claimed

`LiveTrackingMap` theme effects, queue processing, missing queue delays and stale
vehicle behavior were identified during the audit but are outside this round.
Its supplied body remains external and untouched; no guessed implementation of
its missing helpers was substituted into a purported complete production screen.

Still needed for production acceptance:

- Real GeoJSON and original artwork.
- Remaining `StopMarker`, `StringOnlyMarker`, `LineDetailsMarker`,
  `LiveTrackingTransportLine`, `defaultMapSetting()`, `offsetByMeters()` and actual
  SDK-bound models/interfaces. The demo's settings/theme/model stand-ins do not
  prove these production helpers compatible.
- Full module graph, Java/binary/native dependencies and persisted map objects.
- Production R8 rules/configuration; this demo still has shrinking disabled.
- A runtime environment without GMS/microG. APK/dependency isolation is checked,
  but the connected phone contains a GMS-package implementation with microG.
- Location, search, geocoding and directions behavior outside the map renderer.

See [validation results and profile methodology](map-validation.md) and
[the folder walkthrough](map-architecture-explained.md) for the remaining evidence.
