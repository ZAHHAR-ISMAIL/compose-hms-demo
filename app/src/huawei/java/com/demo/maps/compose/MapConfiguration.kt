package com.demo.maps.compose

import androidx.compose.runtime.Immutable
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.MapStyleOptions

/** Huawei configuration models; the Google flavor aliases the official Compose models. */
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
