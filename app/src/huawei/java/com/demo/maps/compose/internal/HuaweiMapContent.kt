package com.demo.maps.compose.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.GoogleMapComposable
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.MarkerState
import com.demo.maps.model.Circle
import com.demo.maps.model.Polygon
import com.demo.maps.model.Polyline
import com.huawei.hms.maps.HuaweiMap
import java.util.UUID

/**
 * Embed one native HMS map, then compose the screen's overlay declarations into that map.
 * The surrounding Android UI and the overlay tree use different Compose appliers: ordinary
 * UI produces views/layout nodes, whereas HuaweiMapApplier produces native map overlays.
 */
@Composable
internal fun RenderHuaweiMap(
    modifier: Modifier,
    cameraPositionState: CameraPositionState,
    properties: MapProperties,
    uiSettings: MapUiSettings,
    contentPadding: PaddingValues,
    contentDescription: String?,
    events: MapEvents,
    content: @Composable @GoogleMapComposable () -> Unit,
) {
    // 1. Take lifecycle/state ownership from this Compose screen (which may be a navigation
    // destination). A saveable unique key prevents two maps from sharing the same native Bundle.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val registry = LocalSavedStateRegistryOwner.current.savedStateRegistry
    val stateKey = rememberSaveable { "map-compat-hms-${UUID.randomUUID()}" }
    // Consume restoration only once; native MapView.onCreate receives this Bundle in the host.
    val restoredState = remember(registry, stateKey) { registry.consumeRestoredStateForKey(stateKey) }
    var ready by remember { mutableStateOf<ReadyMap?>(null) }
    // Preserve the parent's CompositionLocals for map content and marker UI (theme, density,
    // demo callbacks, etc.) even though that content runs in a separate map composition.
    val compositionContext = rememberCompositionContext()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Compose padding is in dp/start/end; HMS expects physical left/top/right/bottom pixels.
    // Resolve layout direction first so a screen also behaves correctly in an RTL layout.
    val padding = with(density) {
        listOf(contentPadding.calculateLeftPadding(direction).roundToPx(), contentPadding.calculateTopPadding().roundToPx(),
            contentPadding.calculateRightPadding(direction).roundToPx(), contentPadding.calculateBottomPadding().roundToPx())
    }
    // The map composition is long-lived. Reading this state from inside it delivers current
    // options, lambdas and content without rebuilding the native view on every recomposition.
    val latest = rememberUpdatedState(Parameters(properties, uiSettings, padding, contentDescription, events, content))
    val cameraOwner = remember(cameraPositionState) { Any() }
    // Reserve camera ownership before the asynchronous SDK callback arrives. Disposal must
    // cancel queued animations even when the map has not become ready or laid out yet.
    DisposableEffect(cameraPositionState, cameraOwner) {
        cameraPositionState.bind(cameraOwner)
        onDispose { cameraPositionState.detach(cameraOwner) }
    }

    // 2. Create the Android host through AndroidView. Its asynchronous map callback publishes
    // a usable HuaweiMap; normal state changes update the composition, not this view factory.
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapsInitializer.initialize(context)
            HuaweiMapHostView(context, lifecycle, registry, stateKey, restoredState) { host, map -> ready = ReadyMap(host, map) }
        },
        onRelease = { host -> host.release(); if (ready?.host === host) ready = null },
    )

    // This means getMapAsync completed, not that the first rendered frame/tiles loaded.
    // HuaweiMapScene handles the later onMapLoaded callback separately for camera animation.
    val loaded = ready
    if (loaded != null) {
        DisposableEffect(loaded, cameraPositionState, compositionContext) {
            // 3. One scene contains native handles/listeners for this map. Its applier turns
            // the screen's Marker/Polygon/etc. declarations into retained native objects.
            val scene = HuaweiMapScene(loaded.host, loaded.map, cameraPositionState, latest.value.events, cameraOwner)
            // Apply initial native options before a pending camera command can attach. The map
            // subcomposition's first SideEffect may otherwise run after that command starts.
            val initial = latest.value
            scene.update(initial.properties, initial.settings)
            scene.updateViewport(initial.padding, initial.description)
            val composition = Composition(HuaweiMapApplier(scene), compositionContext)
            var disposed = false
            // The same cleanup can be reached through Compose disposal or host destruction.
            // Make it idempotent, and remove overlays/listeners before native MapView dies.
            val dispose = {
                if (!disposed) {
                    disposed = true
                    composition.dispose()
                    scene.release()
                }
            }
            loaded.host.onBeforeRelease = dispose
            composition.setContent {
                val params = latest.value
                // Apply map-wide mutations after a successful composition. Geometry/marker
                // mutations are handled by the individual ComposeNode updaters below.
                SideEffect {
                    scene.events = params.events
                    scene.update(params.properties, params.settings)
                    scene.updateViewport(params.padding, params.description)
                }
                // This is the original screen's map content: key(), remember(), conditionals,
                // and shared overlay functions execute with HuaweiMapApplier as their applier.
                params.content()
            }
            // Commands requiring dimensions can run only after the native view is laid out.
            scene.attachCamera()
            onDispose { dispose(); loaded.host.onBeforeRelease = null }
        }
    }
}

/** Obtain the current map's scene; an overlay outside map content has no native map to use. */
@Composable
@GoogleMapComposable
internal fun scene(): HuaweiMapScene = (currentComposer.applier as? HuaweiMapApplier)?.scene
    ?: error("Map overlays must be composed inside GoogleMap content")

@Composable
@GoogleMapComposable
internal fun RenderHuaweiMarker(state: MarkerState, options: MarkerOptions, events: MarkerEvents) = RenderMarker(state, options, events, false)

@Composable
@GoogleMapComposable
internal fun RenderHuaweiMarkerComposable(keys: Array<out Any>, state: MarkerState, options: MarkerOptions, events: MarkerEvents, content: @Composable () -> Unit) {
    // HMS markers consume bitmap icons. Render the UI separately and feed its descriptor into
    // the same retained marker node used for regular markers. needsIcon hides it until ready.
    val icon by rememberMarkerIcon(scene(), keys, content)
    RenderMarker(state, options.copy(icon = icon), events, true)
}

/** Create one native marker per Compose node, then update it in place as live data changes. */
@Composable
@GoogleMapComposable
private fun RenderMarker(state: MarkerState, options: MarkerOptions, events: MarkerEvents, needsIcon: Boolean) {
    val scene = scene()
    ComposeNode<MarkerNode, HuaweiMapApplier>(
        factory = { scene.marker(state, options, events, needsIcon) },
        update = {
            // Updater.set invokes the corresponding mutation when that input changes. Position
            // is separate from icon/style, so vehicle motion does not recreate marker bitmaps.
            set(state) { updateState(it) }
            set(state.position) { updatePosition(it) }
            set(options) { updateOptions(it) }
            set(events) { this.events = it }
        },
    )
}

@Composable
@GoogleMapComposable
internal fun RenderHuaweiPolygon(options: PolygonOptions, onClick: (Polygon) -> Unit) {
    val scene = scene()
    ComposeNode<PolygonNode, HuaweiMapApplier>(
        factory = { PolygonNode(scene, options, onClick) },
        update = { set(options) { update(it) }; set(onClick) { this.onClick = it } },
    )
}

@Composable
@GoogleMapComposable
internal fun RenderHuaweiPolyline(options: PolylineOptions, onClick: (Polyline) -> Unit) {
    // Lines follow the same create-once/update/remove lifecycle as polygon nodes.
    val scene = scene()
    ComposeNode<PolylineNode, HuaweiMapApplier>(
        factory = {
            val polyline = checkNotNull(scene.map.addPolyline(com.huawei.hms.maps.model.PolylineOptions().addAll(options.points.map { it.native() })))
            PolylineNode(polyline, onClick) { scene.polylines.remove(polyline.id) }.also { scene.polylines[polyline.id] = it }
        },
        update = { set(options) { update(it) }; set(onClick) { this.onClick = it } },
    )
}

@Composable
@GoogleMapComposable
internal fun RenderHuaweiCircle(options: CircleOptions, onClick: (Circle) -> Unit) {
    // Center/radius/style changes mutate one circle, useful for a moving location/ripple ring.
    val scene = scene()
    ComposeNode<CircleNode, HuaweiMapApplier>(
        factory = {
            val circle = checkNotNull(scene.map.addCircle(com.huawei.hms.maps.model.CircleOptions().center(options.center.native()).radius(options.radius)))
            CircleNode(circle, onClick) { scene.circles.remove(circle.id) }.also { scene.circles[circle.id] = it }
        },
        update = { set(options) { update(it) }; set(onClick) { this.onClick = it } },
    )
}

// Keep the native map paired with the host that owns its lifetime; neither belongs in screen code.
private data class ReadyMap(val host: HuaweiMapHostView, val map: HuaweiMap)
// Snapshot-observed input bundle used to update a retained map composition with current values.
private data class Parameters(
    val properties: MapProperties,
    val settings: MapUiSettings,
    val padding: List<Int>,
    val description: String?,
    val events: MapEvents,
    val content: @Composable @GoogleMapComposable () -> Unit,
)
