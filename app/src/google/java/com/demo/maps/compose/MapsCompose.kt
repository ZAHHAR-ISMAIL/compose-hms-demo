package com.demo.maps.compose

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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
import com.google.maps.android.compose.GoogleMap as NativeMap
import com.google.maps.android.compose.Marker as NativeMarker
import com.google.maps.android.compose.MarkerComposable as NativeMarkerComposable
import com.google.maps.android.compose.Polygon as NativePolygon
import com.google.maps.android.compose.Polyline as NativePolyline
import com.google.maps.android.compose.Circle as NativeCircle
import com.google.maps.android.compose.currentCameraPositionState as nativeCurrentCameraPositionState

/** Uses the official Google composition local and its original state object. */
val currentCameraPositionState: CameraPositionState @Composable get() = nativeCurrentCameraPositionState

/**
 * Direct forwarding to official Maps Compose. The google flavor's models/state are native aliases.
 * Arguments, nested content and callback handles pass through without a provider interface,
 * registration, conversion, extra camera state or custom readiness rules.
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
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    val observation = LocalDemoObservation.current
    SideEffect { observation?.onCamera(cameraPositionState) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    NativeMap(
        modifier = modifier.onSizeChanged { viewport = IntSize((it.width / density).toInt(), (it.height / density).toInt()) },
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        contentPadding = contentPadding,
        contentDescription = contentDescription,
        onMapClick = onMapClick,
        onMapLongClick = onMapLongClick,
        onMapLoaded = { observation?.onLoaded(); onMapLoaded() },
        onMyLocationButtonClick = onMyLocationButtonClick,
        onMyLocationClick = onMyLocationClick,
        content = { CompositionLocalProvider(LocalMapViewport provides viewport, content = content) },
    )
}

/** Forward the original arguments and native callback handles to official Maps Compose. */
@Composable
@GoogleMapComposable
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
) {
    // Diagnostic submissions to official Compose, not a claim about GPU texture uploads.
    if (com.demo.hmscomposeapp.map.MapPerformanceProbe.enabled) {
        val previous = remember { arrayOfNulls<BitmapDescriptor>(1) }
        val created = remember { booleanArrayOf(false) }
        SideEffect {
            if (!created[0]) { com.demo.hmscomposeapp.map.MapPerformanceProbe.marker(false); created[0] = true }
            if (previous[0] !== icon && icon != null) com.demo.hmscomposeapp.map.MapPerformanceProbe.icon()
            previous[0] = icon
        }
    }
    NativeMarker(
    state = state,
    alpha = alpha,
    anchor = anchor,
    draggable = draggable,
    flat = flat,
    icon = icon,
    infoWindowAnchor = infoWindowAnchor,
    rotation = rotation,
    snippet = snippet,
    tag = tag,
    title = title,
    visible = visible,
    zIndex = zIndex,
    onClick = onClick,
    onInfoWindowClick = onInfoWindowClick,
    onInfoWindowClose = onInfoWindowClose,
    onInfoWindowLongClick = onInfoWindowLongClick,
)

}

/** Forward the original marker state, visual keys and content to Google's bitmap renderer. */
@Composable
@GoogleMapComposable
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
) = NativeMarkerComposable(
    *keys,
    state = state,
    alpha = alpha,
    anchor = anchor,
    draggable = draggable,
    flat = flat,
    infoWindowAnchor = infoWindowAnchor,
    rotation = rotation,
    snippet = snippet,
    tag = tag,
    title = title,
    visible = visible,
    zIndex = zIndex,
    onClick = onClick,
    onInfoWindowClick = onInfoWindowClick,
    onInfoWindowClose = onInfoWindowClose,
    onInfoWindowLongClick = onInfoWindowLongClick,
    content = content,
)

/** Forward the original arguments and native callback handles to official Maps Compose. */
@Composable
@GoogleMapComposable
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
) = NativePolygon(
    points = points,
    clickable = clickable,
    fillColor = fillColor,
    geodesic = geodesic,
    holes = holes,
    strokeColor = strokeColor,
    strokeWidth = strokeWidth,
    tag = tag,
    visible = visible,
    zIndex = zIndex,
    onClick = onClick,
)

/** Forward the original arguments and native callback handles to official Maps Compose. */
@Composable
@GoogleMapComposable
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
) = NativePolyline(
    points = points, clickable = clickable, color = color, geodesic = geodesic, tag = tag, visible = visible, width = width, zIndex = zIndex, onClick = onClick,
)

/** Forward the original arguments and native callback handles to official Maps Compose. */
@Composable
@GoogleMapComposable
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
) = NativeCircle(
    center = center,
    clickable = clickable,
    fillColor = fillColor,
    radius = radius,
    strokeColor = strokeColor,
    strokeWidth = strokeWidth,
    tag = tag,
    visible = visible,
    zIndex = zIndex,
    onClick = onClick,
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

/** Actual map dimensions in dp, never the full device screen or physical pixels. */
internal val LocalMapViewport = staticCompositionLocalOf { IntSize.Zero }
