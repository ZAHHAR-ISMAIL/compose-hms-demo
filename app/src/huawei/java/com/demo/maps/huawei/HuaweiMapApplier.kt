// Map subcomposition structure follows Google Maps Compose (Apache-2.0).
// Copyright 2023 Google LLC. Adaptations copyright 2026 hmsComposeApp contributors.
// Licensed under the Apache License, Version 2.0. See docs/licenses/maps-compose/NOTICE and LICENSE.
package com.demo.maps.huawei

import androidx.compose.runtime.AbstractApplier
import androidx.compose.ui.graphics.toArgb
import com.demo.maps.backend.MapEvents
import com.demo.maps.backend.MarkerEvents
import com.demo.maps.compose.CameraMoveStartedReason
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.CircleOptions
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapType
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.MarkerOptions
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.PolygonOptions
import com.demo.maps.compose.PolylineOptions
import com.demo.maps.model.LatLng
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.model.BitmapDescriptor
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.Polygon
import com.huawei.hms.maps.model.Polyline
import com.huawei.hms.maps.model.Circle
import kotlinx.coroutines.CompletableDeferred

/** One retained overlay resource in the map composition, with an explicit native cleanup hook. */
internal interface OverlayNode { fun remove() {} }
// Compose requires a root for its tree; this bookkeeping root has no visible native overlay.
private object RootNode : OverlayNode

/**
 * Apply Compose tree operations to native map overlays rather than Android UI/layout nodes.
 * For example, if (showBus) Marker(...) creates a node when true and removes its native marker
 * when false; key(vehicleId) preserves identity when the surrounding declarations reorder.
 *
 * ComposeNode factories create SDK objects. This applier retains the resulting nodes and ensures
 * that removing/disposing the composition releases those objects instead of leaving map overlays.
 */
internal class HuaweiMapApplier(val scene: HuaweiMapScene) : AbstractApplier<OverlayNode>(RootNode) {
    private val nodes = mutableListOf<OverlayNode>()
    // Compose offers top-down and bottom-up insertion hooks. Register only once, on bottom-up.
    override fun insertTopDown(index: Int, instance: OverlayNode) = Unit
    override fun insertBottomUp(index: Int, instance: OverlayNode) { nodes.add(index, instance) }
    override fun remove(index: Int, count: Int) { repeat(count) { nodes.removeAt(index).remove() } }
    override fun move(from: Int, to: Int, count: Int) {
        // This reorders Compose bookkeeping, not geographic coordinates or the camera. Native
        // draw order comes from each overlay's zIndex; geographic movement uses its own updater.
        val moving = nodes.subList(from, from + count).toList()
        repeat(count) { nodes.removeAt(from) }
        // Removing a block before a later destination shifts that destination left by count.
        nodes.addAll(if (to > from) to - count else to, moving)
    }
    override fun onClear() { nodes.forEach { it.remove() }; nodes.clear() }
}

/**
 * Per-map coordinator: camera connection, map properties, and native event listeners.
 *
 * HMS registers listeners on HuaweiMap, not on each Compose declaration. ID-to-node registries
 * route a native click/drag event to the corresponding node's latest shared callback/state.
 * Each map instance gets its own scene, so multiple maps do not share overlays or camera events.
 */
internal class HuaweiMapScene(val host: HuaweiMapHostView, val map: HuaweiMap, val cameraState: CameraPositionState, var events: MapEvents, private val cameraOwner: Any) {
    // Map creation, layout readiness and the first rendered frame are separate milestones.
    // Initial moves can follow layout; animated moves additionally wait for this first-frame gate.
    private val renderingReady = CompletableDeferred<Unit>()
    private val camera = HuaweiCameraController(map, renderingReady)
    // These registries keep native SDK IDs inside the implementation. Screens receive shared
    // handle interfaces, not the concrete HMS marker/polygon/line/circle classes.
    private val markers = mutableMapOf<String, MarkerNode>()
    val polygons = mutableMapOf<String, PolygonNode>()
    val polylines = mutableMapOf<String, PolylineNode>()
    val circles = mutableMapOf<String, CircleNode>()
    private var moving = false
    private var reason = CameraMoveStartedReason.UNKNOWN
    private var previousProperties: MapProperties? = null
    private var previousSettings: MapUiSettings? = null
    private var released = false

    init {
        cameraState.bind(cameraOwner)
        map.setOnMapClickListener { events.onMapClick(it.shared()) }
        map.setOnMapLongClickListener { events.onMapLongClick(it.shared()) }
        map.setOnMapLoadedCallback {
            if (!released) {
                renderingReady.complete(Unit)
                events.onMapLoaded()
            }
        }
        map.setOnMyLocationButtonClickListener { events.onMyLocationButtonClick() }
        map.setOnMyLocationClickListener { events.onMyLocationClick(it) }
        map.setOnCameraMoveStartedListener {
            // Translate provider reason codes into the shared enum observed by screen code.
            moving = true
            reason = when (it) { 1 -> CameraMoveStartedReason.GESTURE; 2 -> CameraMoveStartedReason.API_ANIMATION; 3 -> CameraMoveStartedReason.DEVELOPER_ANIMATION; else -> CameraMoveStartedReason.UNKNOWN }
            updateCamera()
        }
        map.setOnCameraMoveListener { updateCamera() }
        map.setOnCameraIdleListener { moving = false; updateCamera() }
        map.setOnCameraMoveCanceledListener { moving = false; updateCamera() }
        map.setOnMarkerClickListener { marker -> markers[marker.id]?.let { it.events.onClick(HuaweiMarker(marker)) } ?: false }
        map.setOnInfoWindowClickListener { marker -> markers[marker.id]?.events?.onInfoWindowClick?.invoke(HuaweiMarker(marker)) }
        map.setOnInfoWindowCloseListener { marker -> markers[marker.id]?.events?.onInfoWindowClose?.invoke(HuaweiMarker(marker)) }
        map.setOnInfoWindowLongClickListener { marker -> markers[marker.id]?.events?.onInfoWindowLongClick?.invoke(HuaweiMarker(marker)) }
        map.setOnMarkerDragListener(object : HuaweiMap.OnMarkerDragListener {
            // During a user drag the SDK becomes the position source. MarkerNode avoids writing
            // an older app position back while isDragging is true, and the final event clears it.
            override fun onMarkerDragStart(marker: Marker) { markers[marker.id]?.state?.updateFromBackend(marker.position.shared(), true) }
            override fun onMarkerDrag(marker: Marker) { markers[marker.id]?.state?.updateFromBackend(marker.position.shared(), true) }
            override fun onMarkerDragEnd(marker: Marker) { markers[marker.id]?.state?.updateFromBackend(marker.position.shared(), false) }
        })
        map.setOnPolygonClickListener { polygons[it.id]?.onClick?.invoke(HuaweiPolygon(it)) }
        map.setOnPolylineClickListener { polylines[it.id]?.onClick?.invoke(HuaweiPolyline(it)) }
        map.setOnCircleClickListener { circles[it.id]?.onClick?.invoke(HuaweiCircle(it)) }
    }

    /** Attach the command receiver only after native width/height can support bounds fitting. */
    fun attachCamera() = host.whenLaidOut { if (!released) cameraState.attach(cameraOwner, camera) }
    private fun updateCamera() {
        // Write the observation directly; using the public position setter would issue another
        // native move and turn a camera callback into a command/callback feedback loop.
        if (!released) cameraState.updateFromBackend(map.cameraPosition.shared(), moving, reason)
    }

    /**
     * Apply map-wide configuration when it changes. A live marker can update many times a second;
     * those updates should not repeatedly reset style, map type, zoom limits, or gesture settings.
     * Location permission is supplied by the caller; this layer does not request it implicitly.
     */
    @android.annotation.SuppressLint("MissingPermission") // The caller owns foreground location permission.
    fun update(properties: MapProperties, settings: MapUiSettings) {
        if (properties != previousProperties) {
            map.isBuildingsEnabled = properties.isBuildingEnabled
            map.isIndoorEnabled = properties.isIndoorEnabled
            map.isMyLocationEnabled = properties.isMyLocationEnabled
            map.isTrafficEnabled = properties.isTrafficEnabled
            // Use explicit SDK constants; the shared enum's ordinal is not a provider API contract.
            map.mapType = when (properties.mapType) {
                MapType.NONE -> HuaweiMap.MAP_TYPE_NONE
                MapType.NORMAL -> HuaweiMap.MAP_TYPE_NORMAL
                MapType.SATELLITE -> HuaweiMap.MAP_TYPE_SATELLITE
                MapType.TERRAIN -> HuaweiMap.MAP_TYPE_TERRAIN
                MapType.HYBRID -> HuaweiMap.MAP_TYPE_HYBRID
            }
            map.setMinZoomPreference(properties.minZoomPreference)
            map.setMaxZoomPreference(properties.maxZoomPreference)
            map.setLatLngBoundsForCameraTarget(properties.latLngBoundsForCameraTarget?.native())
            if (properties.mapStyleOptions != previousProperties?.mapStyleOptions) {
                // HMS and Google style JSON formats differ. Flavor-specific raw resources must
                // provide HMS-compatible JSON; reject an invalid style rather than ignore it.
                check(map.setMapStyle(properties.mapStyleOptions?.let { com.huawei.hms.maps.model.MapStyleOptions(it.json) })) {
                    "HMS rejected the map style; supply an HMS style JSON in the huawei resource source set"
                }
            }
            previousProperties = properties
        }
        if (settings != previousSettings) {
            map.uiSettings.apply {
                isCompassEnabled = settings.compassEnabled
                isIndoorLevelPickerEnabled = settings.indoorLevelPickerEnabled
                isMapToolbarEnabled = settings.mapToolbarEnabled
                isMyLocationButtonEnabled = settings.myLocationButtonEnabled
                isRotateGesturesEnabled = settings.rotationGesturesEnabled
                isScrollGesturesEnabled = settings.scrollGesturesEnabled
                isScrollGesturesEnabledDuringRotateOrZoom = settings.scrollGesturesEnabledDuringRotateOrZoom
                isTiltGesturesEnabled = settings.tiltGesturesEnabled
                isZoomControlsEnabled = settings.zoomControlsEnabled
                isZoomGesturesEnabled = settings.zoomGesturesEnabled
            }
            previousSettings = settings
        }
    }

    /** Called by a ComposeNode factory once for that node, not for each new vehicle position. */
    fun marker(state: MarkerState, options: MarkerOptions, events: MarkerEvents, needsIcon: Boolean): MarkerNode {
        val native = checkNotNull(map.addMarker(com.huawei.hms.maps.model.MarkerOptions().position(state.position.native())))
        return MarkerNode(native, state, options, events, needsIcon) { markers.remove(native.id) }
            .also { markers[native.id] = it }
    }

    /** Disconnect the shared state and every map-level listener before the host destroys MapView. */
    fun release() {
        if (released) return
        cameraState.detach(cameraOwner)
        released = true
        map.setOnMapClickListener(null); map.setOnMapLongClickListener(null); map.setOnMapLoadedCallback(null)
        map.setOnMyLocationButtonClickListener(null); map.setOnMyLocationClickListener(null)
        map.setOnCameraMoveStartedListener(null); map.setOnCameraMoveListener(null)
        map.setOnCameraIdleListener(null); map.setOnCameraMoveCanceledListener(null)
        map.setOnMarkerClickListener(null); map.setOnMarkerDragListener(null)
        map.setOnInfoWindowClickListener(null); map.setOnInfoWindowCloseListener(null); map.setOnInfoWindowLongClickListener(null)
        map.setOnPolygonClickListener(null); map.setOnPolylineClickListener(null); map.setOnCircleClickListener(null)
    }
}

/**
 * Retains one native marker and the current shared state/options/events associated with it.
 * Movement, bearing and visual changes mutate this object, preserving its native ID and avoiding
 * the cost and visible flicker of removing/re-adding a marker for every live transport sample.
 */
internal class MarkerNode(
    val marker: Marker,
    var state: MarkerState,
    options: MarkerOptions,
    var events: MarkerEvents,
    private val needsIcon: Boolean,
    private val onRemove: () -> Unit,
) : OverlayNode {
    private var previous: MarkerOptions? = null
    init { bind(); updateOptions(options) }
    private fun bind() = state.bind(this, marker::showInfoWindow, marker::hideInfoWindow)
    fun updateState(value: MarkerState) {
        // A replacement shared state takes ownership of the existing marker, including its
        // info-window functions. The previous state must no longer control this node.
        if (state === value) return
        state.detach(this); state = value; bind()
    }
    // App-driven animation is authoritative except while the user is actively dragging the pin.
    fun updatePosition(value: LatLng) { if (!state.isDragging) marker.position = value.native() }
    @Suppress("DEPRECATION") // Preserve Google anchor semantics on the pinned HMS SDK.
    fun updateOptions(value: MarkerOptions) {
        // Diff options so changing rotation does not also upload an identical bitmap or reset
        // every marker property. Icon comparison uses identity because an Image descriptor can
        // represent a fresh draw into a reused Bitmap instance.
        if (previous?.alpha != value.alpha) marker.alpha = value.alpha
        // Anchor coordinates are fractions of the icon: (0.5, 1) is its bottom-center,
        // not a dp/pixel offset. This preserves the pickup-pin anchor used by the shared screen.
        if (previous?.anchor != value.anchor) marker.setAnchor(value.anchor.x, value.anchor.y)
        if (previous?.draggable != value.draggable) marker.isDraggable = value.draggable
        if (previous?.flat != value.flat) marker.isFlat = value.flat
        if (previous?.icon !== value.icon && (value.icon != null || !needsIcon)) marker.setIcon(value.icon?.native())
        if (previous?.infoWindowAnchor != value.infoWindowAnchor) marker.setInfoWindowAnchor(value.infoWindowAnchor.x, value.infoWindowAnchor.y)
        if (previous?.rotation != value.rotation) marker.rotation = value.rotation
        if (previous?.snippet != value.snippet) marker.snippet = value.snippet
        if (previous?.tag != value.tag) marker.tag = value.tag
        if (previous?.title != value.title) marker.title = value.title
        // A Compose icon is asynchronous. Hide its temporary default pin until the first bitmap
        // exists; regular markers can immediately display the SDK's default pin when icon is null.
        marker.isVisible = value.visible && (!needsIcon || value.icon != null)
        if (previous?.zIndex != value.zIndex) marker.zIndex = value.zIndex
        previous = value
    }
    override fun remove() { state.detach(this); onRemove(); marker.remove() }
}

/**
 * Retained polygon; points and hole rings are converted again only when their lists change.
 * Hole geometry is passed through to HMS, which performs the mask/hole rendering natively.
 * Stroke width is in pixels, matching the shared API and Google's native overlay behavior.
 */
internal class PolygonNode(val polygon: Polygon, var onClick: (com.demo.maps.model.Polygon) -> Unit, private val onRemove: () -> Unit) : OverlayNode {
    private var previous: PolygonOptions? = null
    fun update(value: PolygonOptions) {
        if (previous?.points != value.points) polygon.points = value.points.map { it.native() }
        if (previous?.holes != value.holes) polygon.holes = value.holes.map { ring -> ring.map { it.native() } }
        polygon.isClickable = value.clickable; polygon.fillColor = value.fillColor.toArgb()
        polygon.isGeodesic = value.geodesic; polygon.strokeColor = value.strokeColor.toArgb()
        polygon.strokeWidth = value.strokeWidth; polygon.tag = value.tag
        polygon.isVisible = value.visible; polygon.zIndex = value.zIndex
        previous = value
    }
    override fun remove() { onRemove(); polygon.remove() }
}

/** Retained route line: geometry changes replace points, style changes mutate the same handle. */
internal class PolylineNode(val polyline: Polyline, var onClick: (com.demo.maps.model.Polyline) -> Unit, private val onRemove: () -> Unit) : OverlayNode {
    private var previous: PolylineOptions? = null
    fun update(value: PolylineOptions) {
        if (previous?.points != value.points) polyline.points = value.points.map { it.native() }
        polyline.isClickable = value.clickable; polyline.color = value.color.toArgb(); polyline.isGeodesic = value.geodesic
        polyline.tag = value.tag; polyline.isVisible = value.visible; polyline.width = value.width; polyline.zIndex = value.zIndex
        previous = value
    }
    override fun remove() { onRemove(); polyline.remove() }
}

/** Retained circle; center/radius (meters) changes support live location/ripple visuals in place. */
internal class CircleNode(val circle: Circle, var onClick: (com.demo.maps.model.Circle) -> Unit, private val onRemove: () -> Unit) : OverlayNode {
    fun update(value: CircleOptions) {
        circle.center = value.center.native(); circle.radius = value.radius; circle.isClickable = value.clickable
        circle.fillColor = value.fillColor.toArgb(); circle.strokeColor = value.strokeColor.toArgb()
        // HMS Circle has a generic setTag method; call it explicitly instead of relying on a
        // Kotlin synthetic writable property (the marker/line/polygon SDK signatures differ).
        circle.strokeWidth = value.strokeWidth; circle.setTag(value.tag); circle.isVisible = value.visible; circle.zIndex = value.zIndex
    }
    override fun remove() { onRemove(); circle.remove() }
}

// Shared callback handles deliberately expose only the operations supported by our contract.
// They forward to the live SDK object, so callback-driven tag/position/info-window changes apply
// to that actual overlay while the screen remains independent of Huawei SDK imports.
private class HuaweiMarker(private val marker: Marker) : com.demo.maps.model.Marker {
    override val id get() = marker.id
    override var position: LatLng get() = marker.position.shared(); set(value) { marker.position = value.native() }
    override var rotation: Float get() = marker.rotation; set(value) { marker.rotation = value }
    override var tag: Any? get() = marker.tag; set(value) { marker.tag = value }
    override fun showInfoWindow() = marker.showInfoWindow()
    override fun hideInfoWindow() = marker.hideInfoWindow()
}
private class HuaweiPolygon(private val overlay: Polygon) : com.demo.maps.model.Polygon {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.tag = value }
}
private class HuaweiPolyline(private val overlay: Polyline) : com.demo.maps.model.Polyline {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.tag = value }
}
private class HuaweiCircle(private val overlay: Circle) : com.demo.maps.model.Circle {
    override val id get() = overlay.id
    override var tag: Any? get() = overlay.tag; set(value) { overlay.setTag(value) }
}
