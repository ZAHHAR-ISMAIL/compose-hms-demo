package com.demo.maps.compose

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.LatLng
import com.demo.maps.model.Marker
import com.demo.maps.model.Circle as CircleHandle
import com.demo.maps.model.Polygon as PolygonHandle
import com.demo.maps.model.Polyline as PolylineHandle
import com.demo.maps.compose.internal.CircleOptions
import com.demo.maps.compose.internal.MapEvents
import com.demo.maps.compose.internal.MarkerEvents
import com.demo.maps.compose.internal.MarkerOptions
import com.demo.maps.compose.internal.PolygonOptions
import com.demo.maps.compose.internal.PolylineOptions
import com.demo.maps.compose.internal.RenderHuaweiMap
import com.demo.maps.compose.internal.RenderHuaweiMarker
import com.demo.maps.compose.internal.RenderHuaweiMarkerComposable
import com.demo.maps.compose.internal.RenderHuaweiPolygon
import com.demo.maps.compose.internal.RenderHuaweiPolyline
import com.demo.maps.compose.internal.RenderHuaweiCircle

private val LocalCameraPositionState = staticCompositionLocalOf<CameraPositionState> {
    error("Camera state is available only inside GoogleMap content")
}
val currentCameraPositionState: CameraPositionState @Composable get() = LocalCameraPositionState.current

/**
 * HMS implementation of the familiar Compose entry point. The build selects this declaration;
 * the screen keeps its existing com.demo.maps imports. Native lifecycle and retained overlay
 * rendering are implemented in this flavor's compose/internal folder, shared by every screen.
 */
@Composable
fun GoogleMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    properties: MapProperties = MapProperties(),
    uiSettings: MapUiSettings = MapUiSettings(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentDescription: String? = null,
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: (LatLng) -> Unit = {},
    onMapLoaded: () -> Unit = {},
    onMyLocationButtonClick: () -> Boolean = { false },
    onMyLocationClick: (Location) -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val observation = LocalDemoObservation.current
    SideEffect { observation?.onCamera(cameraPositionState) }
    RenderHuaweiMap(
        modifier, cameraPositionState, properties, uiSettings, contentPadding, contentDescription,
        MapEvents(onMapClick, onMapLongClick, { observation?.onLoaded(); onMapLoaded() },
            onMyLocationButtonClick, onMyLocationClick),
    ) {
        CompositionLocalProvider(LocalCameraPositionState provides cameraPositionState, content = content)
    }
}

/** Update retained native HMS overlays through this flavor's internal rendering functions. */
@Composable
fun Marker(
    state: MarkerState = rememberUpdatedMarkerState(),
    alpha: Float = 1f,
    anchor: Offset = Offset(0.5f, 1f),
    draggable: Boolean = false,
    flat: Boolean = false,
    icon: BitmapDescriptor? = null,
    infoWindowAnchor: Offset = Offset(0.5f, 0f),
    rotation: Float = 0f,
    snippet: String? = null,
    tag: Any? = null,
    title: String? = null,
    visible: Boolean = true,
    zIndex: Float = 0f,
    onClick: (Marker) -> Boolean = { false },
    onInfoWindowClick: (Marker) -> Unit = {},
    onInfoWindowClose: (Marker) -> Unit = {},
    onInfoWindowLongClick: (Marker) -> Unit = {},
) = RenderHuaweiMarker(
    state, MarkerOptions(alpha, anchor, draggable, flat, icon, infoWindowAnchor, rotation, snippet, tag, title, visible, zIndex),
    MarkerEvents(onClick, onInfoWindowClick, onInfoWindowClose, onInfoWindowLongClick),
)

/** Update retained native HMS overlays through this flavor's internal rendering functions. */
@Composable
fun MarkerComposable(
    vararg keys: Any,
    state: MarkerState = rememberUpdatedMarkerState(),
    alpha: Float = 1f,
    anchor: Offset = Offset(0.5f, 1f),
    draggable: Boolean = false,
    flat: Boolean = false,
    infoWindowAnchor: Offset = Offset(0.5f, 0f),
    rotation: Float = 0f,
    snippet: String? = null,
    tag: Any? = null,
    title: String? = null,
    visible: Boolean = true,
    zIndex: Float = 0f,
    onClick: (Marker) -> Boolean = { false },
    onInfoWindowClick: (Marker) -> Unit = {},
    onInfoWindowClose: (Marker) -> Unit = {},
    onInfoWindowLongClick: (Marker) -> Unit = {},
    content: @UiComposable @Composable () -> Unit,
) = RenderHuaweiMarkerComposable(
    keys, state, MarkerOptions(alpha, anchor, draggable, flat, null, infoWindowAnchor, rotation, snippet, tag, title, visible, zIndex),
    MarkerEvents(onClick, onInfoWindowClick, onInfoWindowClose, onInfoWindowLongClick), content,
)

/** Update retained native HMS overlays through this flavor's internal rendering functions. */
@Composable
fun Polygon(
    points: List<LatLng>,
    clickable: Boolean = false,
    fillColor: Color = Color.Black,
    geodesic: Boolean = false,
    holes: List<List<LatLng>> = emptyList(),
    strokeColor: Color = Color.Black,
    strokeWidth: Float = 10f,
    tag: Any? = null,
    visible: Boolean = true,
    zIndex: Float = 0f,
    onClick: (PolygonHandle) -> Unit = {},
) = RenderHuaweiPolygon(
    PolygonOptions(points, clickable, fillColor, geodesic, holes, strokeColor, strokeWidth, tag, visible, zIndex), onClick,
)

/** Update retained native HMS overlays through this flavor's internal rendering functions. */
@Composable
fun Polyline(
    points: List<LatLng>,
    clickable: Boolean = false,
    color: Color = Color.Black,
    geodesic: Boolean = false,
    tag: Any? = null,
    visible: Boolean = true,
    width: Float = 10f,
    zIndex: Float = 0f,
    onClick: (PolylineHandle) -> Unit = {},
) = RenderHuaweiPolyline(
    PolylineOptions(points, clickable, color, geodesic, tag, visible, width, zIndex), onClick,
)

/** Update retained native HMS overlays through this flavor's internal rendering functions. */
@Composable
fun Circle(
    center: LatLng,
    clickable: Boolean = false,
    fillColor: Color = Color.Black,
    radius: Double = 10.0,
    strokeColor: Color = Color.Black,
    strokeWidth: Float = 10f,
    tag: Any? = null,
    visible: Boolean = true,
    zIndex: Float = 0f,
    onClick: (CircleHandle) -> Unit = {},
) = RenderHuaweiCircle(
    CircleOptions(center, clickable, fillColor, radius, strokeColor, strokeWidth, tag, visible, zIndex), onClick,
)

/**
 * Optional demo hook for the existing readiness/fit-route controls around the supplied screen.
 * Production screens do not need this wrapper. It observes state/loading; it never chooses an SDK.
 */
@Composable
internal fun ObserveMapForDemo(
    onCamera: (CameraPositionState) -> Unit,
    onLoaded: () -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDemoObservation provides DemoObservation(onCamera, onLoaded), content = content)
}

private class DemoObservation(val onCamera: (CameraPositionState) -> Unit, val onLoaded: () -> Unit)
private val LocalDemoObservation = staticCompositionLocalOf<DemoObservation?> { null }
