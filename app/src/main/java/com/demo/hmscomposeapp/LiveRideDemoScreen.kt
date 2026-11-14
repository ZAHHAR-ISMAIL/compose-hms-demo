package com.demo.hmscomposeapp

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demo.hmscomposeapp.busondemand.BusOnDemandRideMap
import com.demo.hmscomposeapp.busondemand.LiveLineTransport
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.backend.MapBackend
import com.demo.maps.backend.MapEvents
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.LocalMapBackend
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapType
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Demo harness only. The actual map is the user's unchanged BusOnDemandRideMap logic. */
@Composable
internal fun LiveRideDemoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var running by rememberSaveable { mutableStateOf(true) }
    var night by rememberSaveable { mutableStateOf(false) }
    var hybrid by rememberSaveable { mutableStateOf(false) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val backend = LocalMapBackend.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(baseConfiguration, night) {
        Configuration(baseConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    val route = remember { DemoMapData.route.map { LatLng(it.latitude, it.longitude) } }
    val bounds = remember(route) { LatLngBounds.builder().apply { route.forEach(::include) }.build() }
    val region = remember { listOf(LatLng(24.725, 46.66), LatLng(24.675, 46.66), LatLng(24.675, 46.70), LatLng(24.725, 46.70)) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text("BusOnDemandRideMap", style = MaterialTheme.typography.subtitle1)
            Text(" · ${backend.id}", style = MaterialTheme.typography.caption)
        }
        // The pasted screen remembers properties without mapType/theme keys. Recreate only the
        // demo instance for these controls, preserving the supplied implementation verbatim.
        key(night, hybrid, replay) {
            var loaded by remember { mutableStateOf(false) }
            var camera by remember { mutableStateOf<CameraPositionState?>(null) }
            var transport by remember {
                mutableStateOf<Pair<String, Queue<LiveLineTransport>>>(
                    "demo-bus" to ConcurrentLinkedQueue(listOf(route.last().transport(isAtStop = true))))
            }
            val observer = remember(backend) {
                DemoMapObserver(backend, { camera = it }, { loaded = true })
            }
            LaunchedEffect(loaded) {
                if (!loaded) return@LaunchedEffect
                // Start with one stationary bus sample so the original initial camera animation
                // can finish. A new queue then simulates the next incoming live data batch.
                delay(1_200)
                transport = "demo-bus" to ConcurrentLinkedQueue(
                    route.dropLast(1).asReversed().mapIndexed { index, point ->
                        point.transport(isAtStop = index == route.size - 2)
                    })
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CompositionLocalProvider(LocalMapBackend provides observer, LocalConfiguration provides configuration) {
                    BusOnDemandRideMap(
                        isLiveActive = running,
                        fromCoord = route.first(),
                        toCoord = route.last(),
                        riyadhGeo = region,
                        mapType = if (hybrid) MapType.HYBRID else MapType.NORMAL,
                        bottomContentHeight = 72.dp,
                        modifier = Modifier.fillMaxSize(),
                        liveTransport = transport,
                    )
                }
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), elevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (loaded) R.string.map_ready else R.string.map_loading), modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { camera?.animate(CameraUpdateFactory.newLatLngBounds(bounds, 90), 500) } }) {
                            Text(stringResource(R.string.fit_route))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Button(onClick = { running = !running }) { Text(stringResource(if (running) R.string.pause_ride else R.string.resume_ride)) }
            TextButton(onClick = { night = !night }) { Text(stringResource(if (night) R.string.day_style else R.string.night_style)) }
            TextButton(onClick = { hybrid = !hybrid }) { Text(stringResource(if (hybrid) R.string.normal_map else R.string.hybrid_map)) }
            TextButton(onClick = { replay++ }) { Text("Replay") }
        }
    }
}

private fun LatLng.transport(isAtStop: Boolean) = LiveLineTransport(x = longitude, y = latitude, bearing = 0.0, isAtStop = isAtStop)

/** Observes demo readiness/camera for the surrounding controls without editing the pasted map. */
private class DemoMapObserver(
    private val delegate: MapBackend,
    private val onCamera: (CameraPositionState) -> Unit,
    private val onLoaded: () -> Unit,
) : MapBackend by delegate {
    @Composable
    override fun Map(
        modifier: Modifier,
        cameraPositionState: CameraPositionState,
        properties: MapProperties,
        uiSettings: MapUiSettings,
        contentPadding: PaddingValues,
        contentDescription: String?,
        events: MapEvents,
        content: @Composable () -> Unit,
    ) {
        SideEffect { onCamera(cameraPositionState) }
        delegate.Map(modifier, cameraPositionState, properties, uiSettings, contentPadding, contentDescription,
            events.copy(onMapLoaded = { onLoaded(); events.onMapLoaded() }), content)
    }
}
