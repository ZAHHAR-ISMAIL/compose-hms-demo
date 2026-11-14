package com.demo.maps.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.MapStyleOptions

enum class MapType { NONE, NORMAL, SATELLITE, TERRAIN, HYBRID }

@Immutable
data class MapProperties(
    val isBuildingEnabled: Boolean = false,
    val isIndoorEnabled: Boolean = false,
    val isMyLocationEnabled: Boolean = false,
    val isTrafficEnabled: Boolean = false,
    val latLngBoundsForCameraTarget: LatLngBounds? = null,
    val mapStyleOptions: MapStyleOptions? = null,
    val mapType: MapType = MapType.NORMAL,
    val maxZoomPreference: Float = 21f,
    val minZoomPreference: Float = 3f,
) {
    init { require(minZoomPreference <= maxZoomPreference) }
}

@Immutable
data class MapUiSettings(
    val compassEnabled: Boolean = true,
    val indoorLevelPickerEnabled: Boolean = true,
    val mapToolbarEnabled: Boolean = true,
    val myLocationButtonEnabled: Boolean = true,
    val rotationGesturesEnabled: Boolean = true,
    val scrollGesturesEnabled: Boolean = true,
    val scrollGesturesEnabledDuringRotateOrZoom: Boolean = true,
    val tiltGesturesEnabled: Boolean = true,
    val zoomControlsEnabled: Boolean = true,
    val zoomGesturesEnabled: Boolean = true,
)

data class MarkerOptions(
    val alpha: Float = 1f,
    val anchor: Offset = Offset(0.5f, 1f),
    val draggable: Boolean = false,
    val flat: Boolean = false,
    val icon: BitmapDescriptor? = null,
    val infoWindowAnchor: Offset = Offset(0.5f, 0f),
    val rotation: Float = 0f,
    val snippet: String? = null,
    val tag: Any? = null,
    val title: String? = null,
    val visible: Boolean = true,
    val zIndex: Float = 0f,
)

data class PolygonOptions(
    val points: List<LatLng>,
    val clickable: Boolean = false,
    val fillColor: Color = Color.Black,
    val geodesic: Boolean = false,
    val holes: List<List<LatLng>> = emptyList(),
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 10f,
    val tag: Any? = null,
    val visible: Boolean = true,
    val zIndex: Float = 0f,
)

data class PolylineOptions(
    val points: List<LatLng>,
    val clickable: Boolean = false,
    val color: Color = Color.Black,
    val geodesic: Boolean = false,
    val tag: Any? = null,
    val visible: Boolean = true,
    val width: Float = 10f,
    val zIndex: Float = 0f,
)

data class CircleOptions(
    val center: LatLng,
    val clickable: Boolean = false,
    val fillColor: Color = Color.Black,
    val radius: Double = 10.0,
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 10f,
    val tag: Any? = null,
    val visible: Boolean = true,
    val zIndex: Float = 0f,
)
