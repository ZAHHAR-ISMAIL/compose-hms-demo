package com.demo.hmscomposeapp.map

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import com.demo.hmscomposeapp.R
import com.demo.maps.compose.GoogleMapComposable
import com.demo.maps.compose.MarkerComposable
import com.demo.maps.compose.rememberUpdatedMarkerState
import com.demo.maps.model.LatLng

/** Supplied helper with neutral artwork; coordinate updates retain its native marker. */
@Composable
@GoogleMapComposable
fun BusStopMarker(title: String, latPosition: LatLng, visible: Boolean, onStationClick: () -> Unit) {
    MarkerComposable(state = rememberUpdatedMarkerState(latPosition), visible = visible,
        anchor = Offset(.5f, .5f), onClick = { onStationClick(); false }) {
        Image(painterResource(R.drawable.demo_bus_pickup), contentDescription = title)
    }
}
