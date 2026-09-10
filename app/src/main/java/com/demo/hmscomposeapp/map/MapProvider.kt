package com.demo.hmscomposeapp.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.demo.hmscomposeapp.locationpicker.LatLongitude

interface MapProvider {
    @Composable
    fun Map(marker: LatLongitude, route: List<LatLongitude>, modifier: Modifier,
            onMapTap: (LatLongitude) -> Unit)
}

interface LocationProvider {
    /** Caller owns foreground permission. Cancellation releases location updates. */
    suspend fun currentLocation(): LatLongitude
}
