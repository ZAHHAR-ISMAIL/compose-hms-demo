# Google and Huawei maps: folder guide

**Shared screens stay in `main`. All map compatibility code lives in the two
flavor source sets.** The build picks the appropriate declarations; each screen
keeps the same `com.demo.maps` imports.

There is one Gradle module, `:app`. There is no shared map implementation registry
or application-root provider wrapper. The project uses the existing `google` and
`huawei` flavors.

## 1. Folder structure

```text
app/src/
├── main/java/com/demo/hmscomposeapp/
│   ├── MainActivity.kt                    initializes the selected SDK once
│   ├── busondemand/BusOnDemandRideMap.kt   your shared supplied screen
│   ├── LiveRideDemoScreen.kt              sample data and comparison controls
│   └── map/
│       ├── DemoMapRenderer.kt             small launcher/picker component and test seam
│       └── LocationProvider.kt            location contract, separate from map rendering
│
├── google/java/com/demo/maps/
│   ├── MapsInitializer.kt                 direct Google SDK initialization
│   ├── CameraUpdate.kt                    native command/factory aliases
│   ├── model/
│   │   ├── Geometry.kt                    native coordinate/bounds/camera aliases
│   │   └── MapResources.kt                native style/icon/handle aliases
│   └── compose/
│       ├── MapsCompose.kt                 direct official Compose forwarding
│       ├── MapState.kt                    native state aliases and remember functions
│       └── MapConfiguration.kt            native properties/settings aliases
│
└── huawei/java/com/demo/maps/
    ├── MapsInitializer.kt                 direct HMS SDK initialization
    ├── CameraUpdate.kt                    deferred HMS camera commands
    ├── model/
    │   ├── Geometry.kt                    HMS-compatible coordinates/bounds/camera
    │   └── MapResources.kt                HMS styles/icons/handle implementations
    └── compose/
        ├── MapsCompose.kt                 the same screen-facing function signatures
        ├── MapState.kt                    HMS-compatible camera/marker state
        ├── MapConfiguration.kt            HMS-compatible properties/settings
        └── internal/                      native HMS implementation details
            ├── HuaweiMapContent.kt        native MapView and nested map composition
            ├── HuaweiMapHostView.kt       lifecycle and saved native state
            ├── HuaweiMapApplier.kt        retained overlays, updates and events
            ├── HuaweiCameraController.kt  native animation/cancellation bridge
            ├── CameraController.kt        internal camera-state command contract
            ├── ComposeMarkerRenderer.kt   Compose content rendered as marker bitmaps
            └── OverlayParameters.kt       internal overlay arguments and callbacks
```

There is no `app/src/main/java/com/demo/maps` folder. The folders above are packages
inside the existing app, not extra modules or build configurations.

Google and Huawei style JSON remains in their respective flavor resource folders,
under the same raw resource names. A flavor-specific `map_provider_name` string
labels the comparison screen; device service detection does not select the SDK.

## 2. How one import works in both builds

A shared screen imports:

```kotlin
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.rememberCameraPositionState
import com.demo.maps.model.LatLng
```

The compiler builds different source combinations:

```text
Google APK = main screens + google declarations + Google SDK dependencies
Huawei APK = main screens + huawei declarations + HMS dependencies
```

Both flavors declare the same package and supported function/type names. Only one
flavor is compiled into an APK. Kotlin files in a flavor do not override classes
in main, so the map declarations are kept entirely in the flavor source sets.

The screen code remains:

```kotlin
val camera = rememberCameraPositionState()

GoogleMap(cameraPositionState = camera) {
    Polygon(points = worldBoundaries, holes = listOf(riyadhGeo))
    MarkerComposable(state = markerState, rotation = bearing) {
        ExistingBusIcon()
    }
}
```

### Google path

[MapsCompose.kt](../app/src/google/java/com/demo/maps/compose/MapsCompose.kt) calls
the official library directly:

```kotlin
// The complete function forwards all supported arguments.
NativeMap(
    cameraPositionState = cameraPositionState,
    properties = properties,
    uiSettings = uiSettings,
    contentPadding = contentPadding,
    content = content,
)
```

The original objects reach Google. For example, Google's
[Geometry.kt](../app/src/google/java/com/demo/maps/model/Geometry.kt) contains:

```kotlin
typealias LatLng = com.google.android.gms.maps.model.LatLng
```

And [MapState.kt](../app/src/google/java/com/demo/maps/compose/MapState.kt) contains:

```kotlin
typealias CameraPositionState = com.google.maps.android.compose.CameraPositionState
```

An alias is another Kotlin source name for the same class. It does not construct a
wrapper, copy a coordinate or introduce another state object. State factories
forward to Google's official `remember…` functions and retain its original savers.
Google owns the native view, overlays, camera behavior and marker bitmap rendering.

### Huawei path

[MapsCompose.kt](../app/src/huawei/java/com/demo/maps/compose/MapsCompose.kt) exposes
the same function signatures and calls the native HMS implementation in `internal`.
HMS models/state retain the existing behavior; this cleanup changes organization
and removes the former routing layer.

The implementation files serve concrete Android/Compose responsibilities:

| Internal file | Why HMS needs it |
| --- | --- |
| [HuaweiMapContent.kt](../app/src/huawei/java/com/demo/maps/compose/internal/HuaweiMapContent.kt) | Hosts the native map and composes nested marker/shape declarations. |
| [HuaweiMapHostView.kt](../app/src/huawei/java/com/demo/maps/compose/internal/HuaweiMapHostView.kt) | Forwards lifecycle, restoration, layout readiness and destruction. |
| [HuaweiMapApplier.kt](../app/src/huawei/java/com/demo/maps/compose/internal/HuaweiMapApplier.kt) | Creates overlays once, updates them in place, handles events and removes them on disposal. |
| [HuaweiCameraController.kt](../app/src/huawei/java/com/demo/maps/compose/internal/HuaweiCameraController.kt) | Translates deferred camera commands and bridges animation callbacks to cancellable coroutines. |
| [CameraController.kt](../app/src/huawei/java/com/demo/maps/compose/internal/CameraController.kt) | Connects HMS camera state to that native controller. |
| [ComposeMarkerRenderer.kt](../app/src/huawei/java/com/demo/maps/compose/internal/ComposeMarkerRenderer.kt) | Draws Compose marker content into cached native bitmap icons. |
| [OverlayParameters.kt](../app/src/huawei/java/com/demo/maps/compose/internal/OverlayParameters.kt) | Holds arguments/callbacks for retained HMS nodes to compare and update. |

These files implement the Compose integration missing from the native MapView.
They are used by all screens, without copies of individual screen implementations.
Overlay argument classes are internal to Huawei; Google forwards named arguments
straight into the official functions.

## 3. Initialization and the comparison demo

`MainActivity` initializes before composing the app:

```kotlin
MapsInitializer.initialize(applicationContext)
setContent {
    HmsComposeAppTheme {
        MapDemo()
    }
}
```

Each flavor's `MapsInitializer` calls its SDK directly. There is no installation
step or global map implementation registration. This also preserves the supplied
screen's qualified initializer call.

Google's command/icon factories are native and require SDK setup before use. A
camera command constructed before a MapView exists is different from one created
before SDK initialization. The HMS view also initializes its native SDK when hosted.

The surrounding demo uses a small internal `ObserveMapForDemo` helper, defined in
each flavor's `MapsCompose.kt`, to receive camera/loading observations. It powers
the existing “Map ready” and “Fit route” controls without modifying the supplied
screen. It does not select a map implementation and is unnecessary for production
screens. Provider labels come from flavor resources.

`DemoMapRenderer` is only the launcher's small marker/route UI and picker test seam.
The complex supplied screen calls the map functions directly. Location uses its
separate interface and flavor-specific implementation.

## 4. What changes in your existing screens

The runnable [BusOnDemandRideMap](../app/src/main/java/com/demo/hmscomposeapp/busondemand/BusOnDemandRideMap.kt)
and the [reference diff](reference/bus-on-demand/imports-only.diff) remain unchanged:
**12 map imports and one qualified initializer reference** differ from the
neutralized original. The queue, interpolation, bearing, camera effects and map
composition remain the supplied code.

The cleanup does not require another screen import migration. Parameter names and
defaults are preserved across the two flavor entry points. Their signatures and
defaults must remain aligned as supported APIs are extended.

Native Google aliases can expose members not implemented by Huawei. Compile both
flavors when migrating helpers. Java/reflection/precompiled library boundaries and
persisted SDK objects need an inventory: aliases do not create replacement JVM
classes or exchange saved-state formats between providers. Google keeps native
Parcelable models/savers; HMS keeps the existing scalar savers.

See [the API and migration reference](map-compatibility.md) for the supported subset,
and [the validation record](map-validation.md) for actual results. The existing HMS
world-mask fill difference and the lack of a clean GMS-free device remain recorded
limitations; this structural cleanup does not resolve those rendering/environment
issues.
