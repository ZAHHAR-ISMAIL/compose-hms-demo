package com.demo.hmscomposeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.hmscomposeapp.busondemand.LiveBusTransportMarker
import com.demo.hmscomposeapp.map.BusStopMarker
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.compose.*
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import kotlinx.coroutines.delay

/** Synthetic station positions and substitute artwork, using the actual production helper contract. */
internal data class DemoStation(val stationId: Int, val coordinate: LatLng) : ClusterItem {
    override fun getPosition() = coordinate
    override fun getTitle() = "Demo station $stationId"
    override fun getSnippet() = "Synthetic station, substitute artwork"
    override fun getZIndex(): Float? = 0f
}

@Composable
internal fun ClusteringDemoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var count by remember { mutableIntStateOf(200) }
    var clusters by remember { mutableStateOf(true) }
    var moving by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var event by remember { mutableStateOf("Tap a station, cluster or vehicle") }
    val stations = remember(count) { List(count) { index ->
        DemoStation(index, LatLng(24.7 + ((index / 40) - 10) * .001, 46.67 + ((index % 40) - 20) * .001))
    } }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(24.7,46.67), 13f) }
    LaunchedEffect(moving) { if (moving) while (true) { delay(50); tick++ } }
    Column(Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Clustering demo", modifier = Modifier.padding(12.dp))
        }
        Text("Synthetic stations/boundaries · substitute marker artwork", style = MaterialTheme.typography.caption)
        Text(event)
        GoogleMap(Modifier.fillMaxWidth().weight(1f), cameraPositionState = camera) {
            if (clusters) AppClusteringWrapper(
                items = stations,
                markerImageResId = R.drawable.demo_bus_pickup,
                onClusterClick = { event = "Cluster: ${it.size} stations"; true },
                onClusterItemClick = { event = it.title.orEmpty(); false },
                onExternalItemClick = { event = "Marker ${it.id}"; false },
            )
            LiveBusTransportMarker(LatLng(24.7, 46.67 + kotlin.math.sin(tick / 50.0) * .005),
                iconResId = R.drawable.demo_live_bus, isAtStop = !moving, bearing = (tick % 360).toFloat())
            BusStopMarker("Moving stop helper", LatLng(24.704,46.668), visible = true) { event = "Stop clicked (unconsumed)" }
        }
        Row {
            TextButton(onClick = { clusters = !clusters }) { Text(if (clusters) "Remove clusters" else "Add clusters") }
            TextButton(onClick = { moving = !moving }) { Text(if (moving) "Stop bus" else "Move bus") }
            TextButton(onClick = { count = if (count == 200) 1000 else 200 }) { Text("$count stations") }
        }
    }
}
