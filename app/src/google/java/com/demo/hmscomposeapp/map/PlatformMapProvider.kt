package com.demo.hmscomposeapp.map

import android.content.Context
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

fun createMapProvider(): MapProvider = GoogleMapProvider

fun createLocationProvider(context: Context): LocationProvider = PlatformLocationProvider(context)

private object GoogleMapProvider : MapProvider {
    @Composable
    override fun Map(
        marker: LatLongitude,
        route: List<LatLongitude>,
        modifier: Modifier,
        onMapTap: (LatLongitude) -> Unit,
    ) {
        val markerPosition = marker.toGoogleLatLng()
        val routePoints = remember(route) { route.map(LatLongitude::toGoogleLatLng) }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(markerPosition, DEFAULT_ZOOM)
        }
        val uiSettings = remember { MapUiSettings(zoomControlsEnabled = true) }
        val density = LocalDensity.current
        val cameraPadding = with(density) { CAMERA_PADDING_DP.dp.roundToPx() }
        val markerTitle = stringResource(R.string.selected_location_marker)
        var mapLoaded by remember { mutableStateOf(false) }

        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            onMapLoaded = { mapLoaded = true },
            onMapClick = { onMapTap(LatLongitude(it.latitude, it.longitude)) },
        ) {
            Marker(
                state = rememberUpdatedMarkerState(position = markerPosition),
                title = markerTitle,
            )
            if (routePoints.size >= 2) {
                Polyline(points = routePoints, color = Color.Black, width = ROUTE_WIDTH_PX)
            }
        }

        LaunchedEffect(mapLoaded, marker, route) {
            if (!mapLoaded) return@LaunchedEffect
            val visiblePoints = routePoints + markerPosition
            val distinctPoints = visiblePoints.distinctBy { it.latitude to it.longitude }
            val update = if (distinctPoints.size >= 2) {
                val bounds = LatLngBounds.builder().apply { distinctPoints.forEach(::include) }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, cameraPadding)
            } else {
                CameraUpdateFactory.newLatLngZoom(markerPosition, DEFAULT_ZOOM)
            }
            cameraPositionState.move(update)
        }
    }
}

private fun LatLongitude.toGoogleLatLng() = LatLng(latitude, longitude)

private const val DEFAULT_ZOOM = 14f
private const val CAMERA_PADDING_DP = 72
private const val ROUTE_WIDTH_PX = 8f
