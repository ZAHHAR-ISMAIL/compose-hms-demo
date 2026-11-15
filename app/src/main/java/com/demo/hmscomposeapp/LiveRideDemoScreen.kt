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
import com.demo.hmscomposeapp.busondemand.LocalRippleTiming
import com.demo.hmscomposeapp.busondemand.RippleTiming
import androidx.compose.ui.platform.LocalContext
import com.demo.hmscomposeapp.map.RegionBoundsLocalProvider
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.ObserveMapForDemo
import com.demo.maps.compose.MapType
import com.demo.maps.compose.isSupported
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
    val hybridSupported = MapType.HYBRID.isSupported()
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(baseConfiguration, night) {
        Configuration(baseConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    val route = remember { DemoMapData.route.map { LatLng(it.latitude, it.longitude) } }
    val bounds = remember(route) { LatLngBounds.builder().apply { route.forEach(::include) }.build() }
    val context = LocalContext.current
    val region = remember(context) { RegionBoundsLocalProvider(context).getCoordinatesPair().riyadh }
    val rippleTiming = remember(running) { RippleTiming(enabled = running) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text("BusOnDemandRideMap", style = MaterialTheme.typography.subtitle1)
            Text(" · ${stringResource(R.string.map_provider_name)}", style = MaterialTheme.typography.caption)
        }
        Text("Synthetic region boundary · substitute bus/tracker artwork", style = MaterialTheme.typography.caption)
        // The pasted screen remembers properties without mapType/theme keys. Recreate only the
        // demo instance for these controls, preserving the supplied implementation verbatim.
        key(night, hybrid, replay) {
            var loaded by remember { mutableStateOf(false) }
            var camera by remember { mutableStateOf<CameraPositionState?>(null) }
            var transport by remember {
                mutableStateOf<Pair<String, Queue<LiveLineTransport>>>(
                    "demo-bus" to ConcurrentLinkedQueue(listOf(route.last().transport(isAtStop = true))))
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
                CompositionLocalProvider(LocalConfiguration provides configuration, LocalRippleTiming provides rippleTiming) {
                    ObserveMapForDemo(onCamera = { camera = it }, onLoaded = { loaded = true }) {
                        BusOnDemandRideMap(
                            isLiveActive = running,
                            fromCoord = route.first(),
                            toCoord = route.last(),
                            riyadhGeo = region,
                            mapType = if (hybrid && hybridSupported) MapType.HYBRID else MapType.NORMAL,
                            bottomContentHeight = 72.dp,
                            modifier = Modifier.fillMaxSize(),
                            liveTransport = transport,
                        )
                    }
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
            TextButton(enabled = hybridSupported, onClick = { hybrid = !hybrid }) {
                Text(if (hybridSupported) stringResource(if (hybrid) R.string.normal_map else R.string.hybrid_map) else "Hybrid unavailable")
            }
            TextButton(onClick = { replay++ }) { Text("Replay") }
        }
    }
}

private fun LatLng.transport(isAtStop: Boolean) = LiveLineTransport(x = longitude, y = latitude, bearing = 0.0, isAtStop = isAtStop)
