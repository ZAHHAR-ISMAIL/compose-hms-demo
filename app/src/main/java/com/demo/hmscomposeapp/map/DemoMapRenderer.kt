package com.demo.hmscomposeapp.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demo.hmscomposeapp.R
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.Marker
import com.demo.maps.compose.Polyline
import com.demo.maps.compose.rememberCameraPositionState
import com.demo.maps.compose.rememberUpdatedMarkerState
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds

/** Test seam for the small launcher/picker UI, independent of the complex supplied screen. */
interface DemoMapRenderer {
    @Composable
    fun Map(marker: LatLongitude, route: List<LatLongitude>, modifier: Modifier,
            onMapTap: (LatLongitude) -> Unit)
}

/** Launcher/picker demo component. Flavor selection happens in the imported map functions. */
fun createDemoMapRenderer(): DemoMapRenderer = ComposeDemoMapRenderer

private object ComposeDemoMapRenderer : DemoMapRenderer {
    @Composable
    override fun Map(marker: LatLongitude, route: List<LatLongitude>, modifier: Modifier, onMapTap: (LatLongitude) -> Unit) {
        val position = LatLng(marker.latitude, marker.longitude)
        val points = remember(route) { route.map { LatLng(it.latitude, it.longitude) } }
        val camera = rememberCameraPositionState { this.position = CameraPosition.fromLatLngZoom(position, 14f) }
        val padding = with(LocalDensity.current) { 72.dp.roundToPx() }
        val markerTitle = stringResource(R.string.selected_location_marker)
        var loaded by remember { mutableStateOf(false) }
        GoogleMap(modifier = modifier, cameraPositionState = camera, onMapLoaded = { loaded = true },
            onMapClick = { onMapTap(LatLongitude(it.latitude, it.longitude)) }) {
            Marker(state = rememberUpdatedMarkerState(position), title = markerTitle)
            if (points.size >= 2) Polyline(points = points, color = Color.Black, width = 8f)
        }
        LaunchedEffect(loaded, marker, route) {
            if (!loaded) return@LaunchedEffect
            val visible = (points + position).distinct()
            camera.move(if (visible.size > 1) {
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { visible.forEach(::include) }.build(), padding)
            } else CameraUpdateFactory.newLatLngZoom(position, 14f))
        }
    }
}
