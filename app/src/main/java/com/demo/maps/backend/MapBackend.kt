package com.demo.maps.backend

import android.content.Context
import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.demo.maps.CameraUpdate
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.CircleOptions
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.MarkerOptions
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.PolygonOptions
import com.demo.maps.compose.PolylineOptions
import com.demo.maps.model.Circle
import com.demo.maps.model.LatLng
import com.demo.maps.model.Marker
import com.demo.maps.model.Polygon
import com.demo.maps.model.Polyline

/** Implemented in the google/huawei flavor source sets. Methods execute on the map's UI thread. */
interface CameraController {
    fun move(update: CameraUpdate)
    suspend fun animate(update: CameraUpdate, durationMs: Int)
    fun stopAnimation()
}

data class MapEvents(
    val onMapClick: (LatLng) -> Unit,
    val onMapLongClick: (LatLng) -> Unit,
    val onMapLoaded: () -> Unit,
    val onMyLocationButtonClick: () -> Boolean,
    val onMyLocationClick: (Location) -> Unit,
)

data class MarkerEvents(
    val onClick: (Marker) -> Boolean,
    val onInfoWindowClick: (Marker) -> Unit,
    val onInfoWindowClose: (Marker) -> Unit,
    val onInfoWindowLongClick: (Marker) -> Unit,
)

interface MapBackend {
    val id: String
    fun initialize(context: Context)

    @Composable
    fun Map(
        modifier: Modifier,
        cameraPositionState: CameraPositionState,
        properties: MapProperties,
        uiSettings: MapUiSettings,
        contentPadding: PaddingValues,
        contentDescription: String?,
        events: MapEvents,
        content: @Composable () -> Unit,
    )

    @Composable fun Marker(state: MarkerState, options: MarkerOptions, events: MarkerEvents)
    @Composable fun MarkerComposable(keys: Array<out Any>, state: MarkerState, options: MarkerOptions, events: MarkerEvents, content: @Composable () -> Unit)
    @Composable fun Polygon(options: PolygonOptions, onClick: (Polygon) -> Unit)
    @Composable fun Polyline(options: PolylineOptions, onClick: (Polyline) -> Unit)
    @Composable fun Circle(options: CircleOptions, onClick: (Circle) -> Unit)
}
