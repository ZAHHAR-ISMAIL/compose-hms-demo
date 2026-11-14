package com.demo.hmscomposeapp.busondemand

// Compile context omitted from the pasted snippet. Demo-only dependencies live in BusOnDemandDemoSupport.kt.
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.demo.hmscomposeapp.R
import java.util.Queue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// BEGIN PASTED SCREEN: only map imports and the qualified initializer below differ.
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.MapStyleOptions
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapType
import com.demo.maps.compose.MarkerComposable
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.Polygon
import com.demo.maps.compose.rememberCameraPositionState


private data class AnimatedMarkerState(
    val id: String,
    val position: LatLng,
    val bearing: Float,
    val isAtStop: Boolean
)

@Composable
fun BusOnDemandRideMap(
    isLiveActive: Boolean,
    fromCoord: LatLng?,
    toCoord: LatLng?,
    riyadhGeo: List<LatLng>?,
    mapType: MapType = MapType.NORMAL,
    bottomContentHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
    liveTransport: Pair<String, Queue<LiveLineTransport>>?
) {
    val worldBoundaries = listOf(
        LatLng(-85.0511, -180.0),
        LatLng(-85.0511, 0.0),
        LatLng(-85.0511, 179.9999),
        LatLng(85.0511, 179.9999),
        LatLng(85.0511, 0.0),
        LatLng(85.0511, -180.0)
    )
    val context = LocalContext.current
    // Add this line to initialize the Maps SDK early
    remember {
        com.demo.maps.MapsInitializer.initialize(context)
        null
    }
    val isDarkTheme = isSystemInDarkTheme()

    val mapStyleOptions = remember(isDarkTheme) {
        val styleRes = if (isDarkTheme) R.raw.dark_map_style else R.raw.map_style
        MapStyleOptions.loadRawResourceStyle(context, styleRes)
    }

    val properties = remember {
        MapProperties(
            isMyLocationEnabled = false,
            mapType = mapType,
            mapStyleOptions = mapStyleOptions,
        )
    }

    // State to hold the current position and bearing of all animated markers
    val animatedMarker = remember { mutableStateOf<AnimatedMarkerState?>(null) }
    val cameraPositionState: CameraPositionState = rememberCameraPositionState()

    val busPosition = animatedMarker.value?.position
    var initialBusZoomDone by remember { mutableStateOf(false) }

    LaunchedEffect(fromCoord, toCoord, riyadhGeo, busPosition) {
        runCatching {
            if (fromCoord != null && busPosition != null && !initialBusZoomDone) {
                if (fromCoord.latitude == busPosition.latitude && fromCoord.longitude == busPosition.longitude) {
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(fromCoord, 15f)
                    )
                } else {
                    val builder = LatLngBounds.builder()
                    builder.include(fromCoord)
                    builder.include(busPosition)
                    val bounds = builder.build()
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngBounds(bounds, 150)
                    )
                }
                initialBusZoomDone = true
            } else if (fromCoord != null && !initialBusZoomDone) {
                cameraPositionState.move(
                    update = CameraUpdateFactory.newLatLngZoom(fromCoord, 16f)
                )
            } else if (riyadhGeo != null && riyadhGeo.isNotEmpty() && !initialBusZoomDone) {
                val builder = LatLngBounds.builder()
                riyadhGeo.forEach {
                    builder.include(it)
                }
                val bounds = builder.build()
                cameraPositionState.move(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 0)
                )
            }
        }.onFailure {
            Timber.e(it)
        }
    }

    LaunchedEffect(key1 = liveTransport, key2 = isLiveActive) {
        liveTransport ?: return@LaunchedEffect
        val queue = liveTransport.second
        val id = liveTransport.first

        // Launch a coroutine for each transport to handle its animation independently
        if (isLiveActive)
            launch {
                while (isActive && isLiveActive && queue.isNotEmpty()) {
                    // Find the current state of this vehicle to use as the starting point

                    if (animatedMarker.value == null) {
                        // First point for this vehicle. Place it on the map and wait for the next one.
                        val initialTransport = queue.poll() ?: break
                        if (initialTransport.y != null && initialTransport.x != null) {
                            val initialPosition = LatLng(initialTransport.y!!, initialTransport.x!!)
                            val initialState = AnimatedMarkerState(
                                id = id,
                                position = initialPosition,
                                bearing = initialTransport.bearing?.toFloat() ?: 0f,
                                isAtStop = initialTransport.isAtStop == true
                            )
                            animatedMarker.value = initialState
                        }

                    } else {
                        // We have a starting point, now get the destination
                        val transport = queue.poll() ?: break
                        if (transport.y == null || transport.x == null) continue

                        val startPosition = animatedMarker.value!!.position
                        val endPosition = LatLng(transport.y!!, transport.x!!)

                        // Animate between the two points. Using a fixed duration as timestamps are unreliable.
                        val duration = 2000L // 2 seconds for each segment
                        val actualBearing =
                            if (transport.bearing != null && transport.bearing != 0.0) {
                                transport.bearing?.toFloat() ?: calculateBearing(
                                    startPosition,
                                    endPosition
                                )
                            } else {
                                calculateBearing(startPosition, endPosition)
                            }
                        val bearing = actualBearing
                        val isAtStop = transport.isAtStop == true

                        val animationStartTime = System.currentTimeMillis()
                        var elapsedTime = 0L

                        while (elapsedTime < duration) {
                            elapsedTime = System.currentTimeMillis() - animationStartTime
                            val fraction = (elapsedTime.toFloat() / duration).coerceAtMost(1f)

                            // Linear interpolation
                            val lat =
                                (1 - fraction) * startPosition.latitude + fraction * endPosition.latitude
                            val lng =
                                (1 - fraction) * startPosition.longitude + fraction * endPosition.longitude
                            val interpolatedPosition = LatLng(lat, lng)

                            animatedMarker.value = AnimatedMarkerState(
                                id = id,
                                position = interpolatedPosition,
                                bearing = bearing,
                                isAtStop = isAtStop
                            )

                            delay(16) // Target ~60 FPS
                        }

                        // Ensure the marker is exactly at the final position
                        animatedMarker.value = AnimatedMarkerState(
                            id = id,
                            position = endPosition,
                            bearing = bearing,
                            isAtStop = isAtStop
                        )
                    }
                }

            }
    }
    if (toCoord != null && fromCoord != null)
        GoogleMap(
            modifier = modifier.fillMaxSize(),
            properties = properties,
            uiSettings = defaultMapSetting(),
            cameraPositionState = cameraPositionState,
            contentPadding = PaddingValues(bottom = bottomContentHeight)
        ) {
            if (riyadhGeo?.isNotEmpty() == true) {
                // This polygon draws a transparent "hole" for your highlighted region
                // in a large dark overlay that covers the rest of the map.
                Polygon(
                    points = worldBoundaries,
                    holes = listOf(riyadhGeo),
                    fillColor = DemoTheme.firebaseColors.basicColors.black.copy(0.7f),
                    strokeColor = Color.Transparent,
                    zIndex = 1f,
                    strokeWidth = 2f
                )

                // This polygon defines the visible boundary of your highlighted region
                Polygon(
                    points = riyadhGeo,
                    strokeWidth = 2f,
                    strokeColor = Color.Black, // Visible boundary color
                    fillColor = Color.Transparent,// Make the region transparent to show the base map
                    zIndex = 1f // Place on top of the base map
                )
            }

//            fromCoord?.let {
//                FirstStopMarker(
//                    latPosition = it,
//                )
//            }
            fromCoord?.let {
                val markerState = remember { MarkerState(position = it) }
                MarkerComposable(
                    state = markerState,
                    anchor = Offset(.5f, 0.85f),
                    zIndex = 1f
                ) {
                    Box(modifier = Modifier.padding(bottom = dimen5sdp)) {
                        DemoIcon(
                            modifier = Modifier.zIndex(1f),
                            painter = painterResource(id = R.drawable.demo_bus_pickup),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            iconSize = IconSize.S,
                        )
                        Box(
                            modifier = Modifier
                                .size(dimen10sdp)
                                .offset(y = dimen5sdp)
                                .zIndex(0.8f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    color = DemoTheme.firebaseColors.basicColors.white,
                                    shape = DemoTheme.shapes.circularButton
                                )
                                .border(
                                    width = dimen2sdp,
                                    color = DemoTheme.firebaseColors.busOnDemandColors.busOnDemand,
                                    shape = DemoTheme.shapes.circularButton
                                ),
                        )
                    }
                }
            }

            animatedMarker.value?.let { markerState ->
                key(markerState.id) {
                    LiveBusTransportMarker(
                        markerPosition = markerState.position,
                        isVisible = true,
                        iconResId = R.drawable.demo_live_bus,
                        bearing = markerState.bearing,
                        isAtStop = markerState.isAtStop,
                        rippleColorSolid = DemoTheme.firebaseColors.busOnDemandColors.busOnDemand,
                        isBod = true
                    )
                }
            }
        }
}

/**
 * Calculates the bearing (direction) from a start point to an end point.
 */
private fun calculateBearing(start: LatLng, end: LatLng): Float {
    val startLat = Math.toRadians(start.latitude)
    val startLng = Math.toRadians(start.longitude)
    val endLat = Math.toRadians(end.latitude)
    val endLng = Math.toRadians(end.longitude)

    val dLng = endLng - startLng

    val y = sin(dLng) * cos(endLat)
    val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)

    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}