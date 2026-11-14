package com.demo.maps.compose

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.demo.maps.MapsInitializer
import com.demo.maps.backend.MapBackend
import com.demo.maps.backend.MapEvents
import com.demo.maps.backend.MarkerEvents
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.LatLng
import com.demo.maps.model.Marker
import com.demo.maps.model.Circle as CircleHandle
import com.demo.maps.model.Polygon as PolygonHandle
import com.demo.maps.model.Polyline as PolylineHandle

val LocalMapBackend = staticCompositionLocalOf<MapBackend> {
    error("Wrap the application in ProvideMapBackend using the flavor-specific backend")
}
private val LocalCameraPositionState = staticCompositionLocalOf<CameraPositionState> {
    error("Camera state is available only inside GoogleMap content")
}
val currentCameraPositionState: CameraPositionState @Composable get() = LocalCameraPositionState.current

@Composable
fun ProvideMapBackend(backend: MapBackend, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    remember(backend, context) {
        MapsInitializer.install(backend)
        backend.initialize(context)
        backend
    }
    CompositionLocalProvider(LocalMapBackend provides backend, content = content)
}

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
    LocalMapBackend.current.Map(
        modifier, cameraPositionState, properties, uiSettings, contentPadding, contentDescription,
        MapEvents(onMapClick, onMapLongClick, onMapLoaded, onMyLocationButtonClick, onMyLocationClick),
    ) {
        CompositionLocalProvider(LocalCameraPositionState provides cameraPositionState, content = content)
    }
}

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
) = LocalMapBackend.current.Marker(
    state, MarkerOptions(alpha, anchor, draggable, flat, icon, infoWindowAnchor, rotation, snippet, tag, title, visible, zIndex),
    MarkerEvents(onClick, onInfoWindowClick, onInfoWindowClose, onInfoWindowLongClick),
)

/** Content becomes a bitmap marker. Pass keys for visual changes; position/rotation do not invalidate the icon. */
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
    content: @Composable () -> Unit,
) = LocalMapBackend.current.MarkerComposable(
    keys, state, MarkerOptions(alpha, anchor, draggable, flat, null, infoWindowAnchor, rotation, snippet, tag, title, visible, zIndex),
    MarkerEvents(onClick, onInfoWindowClick, onInfoWindowClose, onInfoWindowLongClick), content,
)

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
) = LocalMapBackend.current.Polygon(
    PolygonOptions(points, clickable, fillColor, geodesic, holes, strokeColor, strokeWidth, tag, visible, zIndex), onClick,
)

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
) = LocalMapBackend.current.Polyline(PolylineOptions(points, clickable, color, geodesic, tag, visible, width, zIndex), onClick)

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
) = LocalMapBackend.current.Circle(CircleOptions(center, clickable, fillColor, radius, strokeColor, strokeWidth, tag, visible, zIndex), onClick)
