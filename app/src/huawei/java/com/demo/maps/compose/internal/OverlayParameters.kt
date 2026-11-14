package com.demo.maps.compose.internal

import android.location.Location
import com.demo.maps.model.Marker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.LatLng

/** HMS-only overlay parameters, diffed by retained Compose nodes to avoid recreating SDK objects. */
internal data class MarkerOptions(
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

internal data class PolygonOptions(
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

internal data class PolylineOptions(
    val points: List<LatLng>,
    val clickable: Boolean = false,
    val color: Color = Color.Black,
    val geodesic: Boolean = false,
    val tag: Any? = null,
    val visible: Boolean = true,
    val width: Float = 10f,
    val zIndex: Float = 0f,
)

internal data class CircleOptions(
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

internal data class MapEvents(
    val onMapClick: (LatLng) -> Unit,
    val onMapLongClick: (LatLng) -> Unit,
    val onMapLoaded: () -> Unit,
    val onMyLocationButtonClick: () -> Boolean,
    val onMyLocationClick: (Location) -> Unit,
)

internal data class MarkerEvents(
    val onClick: (Marker) -> Boolean,
    val onInfoWindowClick: (Marker) -> Unit,
    val onInfoWindowClose: (Marker) -> Unit,
    val onInfoWindowLongClick: (Marker) -> Unit,
)
