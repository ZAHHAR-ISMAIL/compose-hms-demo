package com.demo.maps.google

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.demo.maps.CameraUpdate
import com.demo.maps.backend.CameraController
import com.demo.maps.backend.MapBackend
import com.demo.maps.backend.MapEvents
import com.demo.maps.backend.MarkerEvents
import com.demo.maps.compose.CameraMoveStartedReason
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.CircleOptions
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.MarkerOptions
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.PolygonOptions
import com.demo.maps.compose.PolylineOptions
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.Circle
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.Marker
import com.demo.maps.model.Polygon
import com.demo.maps.model.Polyline
import com.google.android.gms.maps.CameraUpdateFactory as NativeUpdates
import com.google.android.gms.maps.model.BitmapDescriptorFactory as NativeIcons
import com.google.android.gms.maps.model.CameraPosition as NativeCameraPosition
import com.google.android.gms.maps.model.LatLng as NativeLatLng
import com.google.android.gms.maps.model.LatLngBounds as NativeBounds
import com.google.maps.android.compose.CameraPositionState as NativeCameraState
import com.google.maps.android.compose.GoogleMap as NativeMap
import com.google.maps.android.compose.Marker as NativeMarker
import com.google.maps.android.compose.MarkerComposable as NativeMarkerComposable
import com.google.maps.android.compose.MarkerState as NativeMarkerState
import com.google.maps.android.compose.Polygon as NativePolygon
import com.google.maps.android.compose.Polyline as NativePolyline
import com.google.maps.android.compose.Circle as NativeCircle
import com.google.maps.android.compose.MapEffect as NativeMapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi

/**
 * Google implementation of the shared map API. The Google flavor's createMapBackend() selects it.
 *
 * A screen calls com.demo.maps.compose.GoogleMap/Marker/Polygon and uses shared model/state types.
 * This object converts those inputs, then calls the official Google Maps Compose functions below.
 * Imports named NativeMap, NativeMarker, and NativeCameraState refer to that official library.
 *
 * Google Maps Compose already owns the native view lifecycle, overlay composition, and custom-icon
 * rendering. Consequently this backend needs adapters rather than the extra view/applier/bitmap
 * classes implemented in the Huawei flavor. Both flavors expose the same screen-facing API.
 */
object GoogleMapBackend : MapBackend {
    override val id = "google"
    /** Native SDK setup; the shared root provider normally passes the application context. */
    override fun initialize(context: Context) {
        com.google.android.gms.maps.MapsInitializer.initialize(context)
    }

    /**
     * Connect one shared camera state to one official Google map and forward its content.
     * The shared state is what the screen owns; nativeState is an implementation detail here.
     */
    @Composable
    @OptIn(MapsComposeExperimentalApi::class)
    override fun Map(
        modifier: Modifier,
        cameraPositionState: CameraPositionState,
        properties: MapProperties,
        uiSettings: MapUiSettings,
        contentPadding: PaddingValues,
        contentDescription: String?,
        events: MapEvents,
        content: @Composable () -> Unit,
    ) {
        // Keep native state/controller instances across recompositions. Recreating them for every
        // bus-position update would lose camera ownership and interrupt in-flight animations.
        val nativeState = remember(cameraPositionState) { NativeCameraState(cameraPositionState.position.native()) }
        val controller = remember(nativeState) { GoogleCameraController(nativeState) }
        var ready by remember(nativeState) { mutableStateOf(false) }
        var size by remember(nativeState) { mutableStateOf(IntSize.Zero) }
        // bind reserves this shared state for this map. detach releases ownership and cancels
        // pending camera work when navigation removes the map or replaces its state object.
        DisposableEffect(cameraPositionState, controller) {
            cameraPositionState.bind(controller)
            onDispose { cameraPositionState.detach(controller) }
        }
        // attach supplies the actual command receiver and flushes queued pre-readiness commands.
        // Bounds fitting needs both a native map and non-zero view dimensions; tile loading is
        // reported separately through onMapLoaded and does not define this readiness check.
        LaunchedEffect(ready, size, cameraPositionState, controller) {
            if (ready && size.width > 0 && size.height > 0) cameraPositionState.attach(controller, controller)
        }
        // Movement can come from gestures as well as screen commands. Mirror native observations
        // back into shared state. updateFromBackend does not issue another move, avoiding a loop.
        LaunchedEffect(nativeState, cameraPositionState) {
            snapshotFlow { Triple(nativeState.position, nativeState.isMoving, nativeState.cameraMoveStartedReason) }
                .collect { (position, moving, reason) ->
                    cameraPositionState.updateFromBackend(position.shared(), moving,
                        CameraMoveStartedReason.entries.firstOrNull { it.name == reason.name } ?: CameraMoveStartedReason.UNKNOWN)
                }
        }
        // Translate configuration only when the shared values change. Style JSON is not converted
        // here: the Google flavor supplies Google-format resources under the shared resource names.
        val nativeProperties = remember(properties) {
            com.google.maps.android.compose.MapProperties(
                isBuildingEnabled = properties.isBuildingEnabled,
                isIndoorEnabled = properties.isIndoorEnabled,
                isMyLocationEnabled = properties.isMyLocationEnabled,
                isTrafficEnabled = properties.isTrafficEnabled,
                latLngBoundsForCameraTarget = properties.latLngBoundsForCameraTarget?.native(),
                mapStyleOptions = properties.mapStyleOptions?.let { com.google.android.gms.maps.model.MapStyleOptions(it.json) },
                mapType = com.google.maps.android.compose.MapType.valueOf(properties.mapType.name),
                maxZoomPreference = properties.maxZoomPreference,
                minZoomPreference = properties.minZoomPreference,
            )
        }
        val nativeSettings = remember(uiSettings) {
            com.google.maps.android.compose.MapUiSettings(
                compassEnabled = uiSettings.compassEnabled,
                indoorLevelPickerEnabled = uiSettings.indoorLevelPickerEnabled,
                mapToolbarEnabled = uiSettings.mapToolbarEnabled,
                myLocationButtonEnabled = uiSettings.myLocationButtonEnabled,
                rotationGesturesEnabled = uiSettings.rotationGesturesEnabled,
                scrollGesturesEnabled = uiSettings.scrollGesturesEnabled,
                scrollGesturesEnabledDuringRotateOrZoom = uiSettings.scrollGesturesEnabledDuringRotateOrZoom,
                tiltGesturesEnabled = uiSettings.tiltGesturesEnabled,
                zoomControlsEnabled = uiSettings.zoomControlsEnabled,
                zoomGesturesEnabled = uiSettings.zoomGesturesEnabled,
            )
        }
        // The official renderer owns MapView and its map-specific Compose tree. Forwarding content
        // allows the screen's existing key/remember/conditional overlay declarations to run there.
        // Callbacks convert SDK coordinates/handles before exposing them to shared screen code.
        NativeMap(
            modifier = modifier.onSizeChanged { size = it },
            cameraPositionState = nativeState,
            properties = nativeProperties,
            uiSettings = nativeSettings,
            contentPadding = contentPadding,
            contentDescription = contentDescription,
            onMapClick = { events.onMapClick(it.shared()) },
            onMapLongClick = { events.onMapLongClick(it.shared()) },
            onMapLoaded = events.onMapLoaded,
            onMyLocationButtonClick = events.onMyLocationButtonClick,
            onMyLocationClick = events.onMyLocationClick,
        ) {
            // This runs inside the official map's composition, after its native map is available.
            NativeMapEffect(nativeState) { ready = true }
            content()
        }
    }

    /**
     * Maintain the two-way marker state connection without competing with an active user drag.
     * One shared MarkerState owns one native marker state for the lifetime of this composition.
     */
    @Composable
    private fun nativeMarkerState(state: MarkerState): NativeMarkerState {
        val native = remember(state) { NativeMarkerState(state.position.native()) }
        // App-driven position changes move the retained marker. While dragging, Google is the
        // position source; writing the old app position here would pull the marker back.
        if (!native.isDragging) native.position = state.position.native()
        DisposableEffect(state, native) {
            state.bind(native, native::showInfoWindow, native::hideInfoWindow)
            onDispose { state.detach(native) }
        }
        LaunchedEffect(native, state) {
            snapshotFlow { native.position to native.isDragging }.collect { (position, dragging) ->
                // Also copy the final sample when a drag has just ended (shared isDragging was true).
                if (dragging || state.isDragging) state.updateFromBackend(position.shared(), dragging)
            }
        }
        return native
    }

    @Composable
    override fun Marker(state: MarkerState, options: MarkerOptions, events: MarkerEvents) {
        val native = nativeMarkerState(state)
        // Position/rotation updates reuse the icon conversion when the descriptor is unchanged.
        val icon = remember(options.icon) { options.icon?.native() }
        NativeMarker(
            state = native, alpha = options.alpha, anchor = options.anchor, draggable = options.draggable,
            flat = options.flat, icon = icon, infoWindowAnchor = options.infoWindowAnchor, rotation = options.rotation,
            snippet = options.snippet, tag = options.tag, title = options.title, visible = options.visible, zIndex = options.zIndex,
            onClick = { events.onClick(GoogleMarker(it)) },
            onInfoWindowClick = { events.onInfoWindowClick(GoogleMarker(it)) },
            onInfoWindowClose = { events.onInfoWindowClose(GoogleMarker(it)) },
            onInfoWindowLongClick = { events.onInfoWindowLongClick(GoogleMarker(it)) },
        )
    }

    /**
     * Delegate Compose-content-to-bitmap rendering to the official library. Visual keys such as
     * an icon/status change are forwarded; moving the marker is a separate state update.
     */
    @Composable
    override fun MarkerComposable(keys: Array<out Any>, state: MarkerState, options: MarkerOptions, events: MarkerEvents, content: @Composable () -> Unit) {
        val native = nativeMarkerState(state)
        NativeMarkerComposable(
            *keys, state = native, alpha = options.alpha, anchor = options.anchor, draggable = options.draggable,
            flat = options.flat, infoWindowAnchor = options.infoWindowAnchor, rotation = options.rotation,
            snippet = options.snippet, tag = options.tag, title = options.title, visible = options.visible, zIndex = options.zIndex,
            onClick = { events.onClick(GoogleMarker(it)) },
            onInfoWindowClick = { events.onInfoWindowClick(GoogleMarker(it)) },
            onInfoWindowClose = { events.onInfoWindowClose(GoogleMarker(it)) },
            onInfoWindowLongClick = { events.onInfoWindowLongClick(GoogleMarker(it)) }, content = content,
        )
    }

    @Composable
    override fun Polygon(options: PolygonOptions, onClick: (Polygon) -> Unit) {
        // Convert outer and hole rings independently, preserving the screen's geometry. Reuse
        // these lists while only styling/callbacks change, rather than rebuilding every point.
        val points = remember(options.points) { options.points.map { it.native() } }
        val holes = remember(options.holes) { options.holes.map { ring -> ring.map { it.native() } } }
        NativePolygon(points = points, holes = holes, clickable = options.clickable, fillColor = options.fillColor,
            geodesic = options.geodesic, strokeColor = options.strokeColor, strokeWidth = options.strokeWidth,
            tag = options.tag, visible = options.visible, zIndex = options.zIndex, onClick = { onClick(GooglePolygon(it)) })
    }

    @Composable
    override fun Polyline(options: PolylineOptions, onClick: (Polyline) -> Unit) {
        // Width uses the native SDK's pixel units, matching the familiar Compose map API.
        val points = remember(options.points) { options.points.map { it.native() } }
        NativePolyline(points = points, clickable = options.clickable, color = options.color, geodesic = options.geodesic,
            tag = options.tag, visible = options.visible, width = options.width, zIndex = options.zIndex,
            onClick = { onClick(GooglePolyline(it)) })
    }

    @Composable
    override fun Circle(options: CircleOptions, onClick: (Circle) -> Unit) {
        // Radius is in meters, unlike pixel-based stroke width. Google handles the overlay node.
        NativeCircle(center = options.center.native(), clickable = options.clickable, fillColor = options.fillColor,
            radius = options.radius, strokeColor = options.strokeColor, strokeWidth = options.strokeWidth,
            tag = options.tag, visible = options.visible, zIndex = options.zIndex, onClick = { onClick(GoogleCircle(it)) })
    }
}

/** Shared camera state calls this interface; only this adapter knows Google's command objects. */
private class GoogleCameraController(private val state: NativeCameraState) : CameraController {
    override fun move(update: CameraUpdate) = state.move(update.native())
    override suspend fun animate(update: CameraUpdate, durationMs: Int) = state.animate(update.native(), durationMs)
    // NativeCameraState exposes move/animate rather than a direct stop method. Moving to its
    // current position interrupts animation without requesting a different camera destination.
    override fun stopAnimation() = state.move(NativeUpdates.newCameraPosition(state.position))
}

// SDK boundary: construct matching native/shared values from their fields. These are explicit
// conversions, not casts; shared coordinates and camera commands are not Google SDK objects.
private fun LatLng.native() = NativeLatLng(latitude, longitude)
private fun NativeLatLng.shared() = LatLng(latitude, longitude)
private fun LatLngBounds.native() = NativeBounds(southwest.native(), northeast.native())
private fun CameraPosition.native() = NativeCameraPosition(target.native(), zoom, tilt, bearing)
private fun NativeCameraPosition.shared() = CameraPosition(target.shared(), zoom, tilt, bearing)
// Semantic commands remain provider-independent until execution. The dimensions-aware bounds
// overload is used only when the caller supplied width/height; otherwise the map's layout is used.
private fun CameraUpdate.native(): com.google.android.gms.maps.CameraUpdate = when (this) {
    is CameraUpdate.Position -> NativeUpdates.newCameraPosition(position.native())
    is CameraUpdate.Target -> zoom?.let { NativeUpdates.newLatLngZoom(target.native(), it) } ?: NativeUpdates.newLatLng(target.native())
    is CameraUpdate.Bounds -> if (width != null && height != null) NativeUpdates.newLatLngBounds(bounds.native(), width!!, height!!, padding)
        else NativeUpdates.newLatLngBounds(bounds.native(), padding)
    is CameraUpdate.Scroll -> NativeUpdates.scrollBy(xPixel, yPixel)
    is CameraUpdate.Zoom -> if (absolute) NativeUpdates.zoomTo(amount) else focus?.let { NativeUpdates.zoomBy(amount, it) } ?: NativeUpdates.zoomBy(amount)
}
/** Translate shared image/resource/default-pin descriptions into Google's native descriptors. */
private fun BitmapDescriptor.native(): com.google.android.gms.maps.model.BitmapDescriptor = when (this) {
    is BitmapDescriptor.Image -> NativeIcons.fromBitmap(bitmap)
    is BitmapDescriptor.Resource -> NativeIcons.fromResource(resourceId)
    is BitmapDescriptor.Default -> NativeIcons.defaultMarker(hue)
}

// Callback handles expose only the operations in our shared interfaces. A screen can read an ID
// or update a tag without importing Google's concrete Marker/Polygon/Polyline/Circle classes.
private class GoogleMarker(private val marker: com.google.android.gms.maps.model.Marker) : Marker {
    override val id get() = marker.id
    override var position: LatLng get() = marker.position.shared(); set(value) { marker.position = value.native() }
    override var rotation: Float get() = marker.rotation; set(value) { marker.rotation = value }
    override var tag: Any? get() = marker.tag; set(value) { marker.tag = value }
    override fun showInfoWindow() = marker.showInfoWindow()
    override fun hideInfoWindow() = marker.hideInfoWindow()
}
private class GooglePolygon(private val overlay: com.google.android.gms.maps.model.Polygon) : Polygon {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.tag = value }
}
private class GooglePolyline(private val overlay: com.google.android.gms.maps.model.Polyline) : Polyline {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.tag = value }
}
private class GoogleCircle(private val overlay: com.google.android.gms.maps.model.Circle) : Circle {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.tag = value }
}
