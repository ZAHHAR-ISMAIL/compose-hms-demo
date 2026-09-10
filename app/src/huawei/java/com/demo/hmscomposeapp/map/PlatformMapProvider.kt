package com.demo.hmscomposeapp.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.demo.hmscomposeapp.R
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.huawei.hms.maps.CameraUpdateFactory
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapView
import com.huawei.hms.maps.MapsInitializer
import com.huawei.hms.maps.model.LatLng
import com.huawei.hms.maps.model.LatLngBounds
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import com.huawei.hms.maps.model.Polyline
import com.huawei.hms.maps.model.PolylineOptions
import java.util.UUID
import kotlin.math.roundToInt

fun createMapProvider(): MapProvider = HuaweiMapProvider

fun createLocationProvider(context: Context): LocationProvider = PlatformLocationProvider(context)

private object HuaweiMapProvider : MapProvider {
    @Composable
    override fun Map(
        marker: LatLongitude,
        route: List<LatLongitude>,
        modifier: Modifier,
        onMapTap: (LatLongitude) -> Unit,
    ) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val savedStateRegistry = LocalSavedStateRegistryOwner.current.savedStateRegistry
        val mapStateKey = rememberSaveable { "$MAP_STATE_KEY_PREFIX-${UUID.randomUUID()}" }
        val restoredState = remember(savedStateRegistry, mapStateKey) {
            savedStateRegistry.consumeRestoredStateForKey(mapStateKey)
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                HuaweiMapHostView(
                    context = context,
                    lifecycle = lifecycle,
                    savedStateRegistry = savedStateRegistry,
                    mapStateKey = mapStateKey,
                    restoredState = restoredState,
                )
            },
            update = { it.update(marker, route, onMapTap) },
            onRelease = HuaweiMapHostView::release,
        )
    }
}

private class HuaweiMapHostView(
    context: Context,
    private val lifecycle: Lifecycle,
    private val savedStateRegistry: SavedStateRegistry,
    private val mapStateKey: String,
    restoredState: Bundle?,
) : FrameLayout(context), LifecycleEventObserver, ComponentCallbacks2 {
    private val mapView: MapView
    private val controller: HuaweiMapController
    private var started = false
    private var resumed = false
    private var released = false

    init {
        MapsInitializer.initialize(context)
        mapView = MapView(context).apply { onCreate(restoredState) }
        controller = HuaweiMapController(mapView)
        addView(
            mapView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        savedStateRegistry.registerSavedStateProvider(mapStateKey) {
            Bundle().also(mapView::onSaveInstanceState)
        }
        lifecycle.addObserver(this)
        context.applicationContext.registerComponentCallbacks(this)
        syncLifecycle()
    }

    fun update(
        marker: LatLongitude,
        route: List<LatLongitude>,
        onMapTap: (LatLongitude) -> Unit,
    ) {
        controller.update(marker, route, onMapTap)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) release() else syncLifecycle()
    }

    private fun syncLifecycle() {
        if (released) return
        val shouldStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val shouldResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

        if (shouldStart && !started) {
            mapView.onStart()
            started = true
        }
        if (shouldResume && !resumed) {
            mapView.onResume()
            resumed = true
        }
        if (!shouldResume && resumed) {
            mapView.onPause()
            resumed = false
        }
        if (!shouldStart && started) {
            mapView.onStop()
            started = false
        }
    }

    fun release() {
        if (released) return
        released = true
        if (resumed) mapView.onPause()
        if (started) mapView.onStop()
        resumed = false
        started = false
        lifecycle.removeObserver(this)
        savedStateRegistry.unregisterSavedStateProvider(mapStateKey)
        context.applicationContext.unregisterComponentCallbacks(this)
        controller.release()
        mapView.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        if (!released) mapView.onLowMemory()
    }

    override fun onTrimMemory(level: Int) = Unit

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

private class HuaweiMapController(private val mapView: MapView) {
    private var map: HuaweiMap? = null
    private var markerOverlay: Marker? = null
    private var routeOverlay: Polyline? = null
    private var marker = LatLongitude()
    private var route = emptyList<LatLongitude>()
    private var onMapTap: (LatLongitude) -> Unit = {}
    private var hasModel = false
    private var released = false
    private var cameraRunnable: Runnable? = null

    init {
        mapView.getMapAsync { readyMap ->
            if (released) return@getMapAsync
            map = readyMap
            readyMap.uiSettings.isZoomControlsEnabled = true
            readyMap.setOnMapClickListener { point ->
                onMapTap(LatLongitude(point.latitude, point.longitude))
            }
            render()
        }
    }

    fun update(
        marker: LatLongitude,
        route: List<LatLongitude>,
        onMapTap: (LatLongitude) -> Unit,
    ) {
        this.onMapTap = onMapTap
        val modelChanged = !hasModel || this.marker != marker || this.route != route
        this.marker = marker
        this.route = route
        hasModel = true
        if (modelChanged) render()
    }

    private fun render() {
        val readyMap = map ?: return
        if (!hasModel) return

        val markerPosition = marker.toHuaweiLatLng()
        markerOverlay = markerOverlay?.also { it.position = markerPosition }
            ?: readyMap.addMarker(
                MarkerOptions().position(markerPosition)
                    .title(mapView.context.getString(R.string.selected_location_marker)),
            )

        val routePoints = route.map(LatLongitude::toHuaweiLatLng)
        if (routePoints.size < 2) {
            routeOverlay?.remove()
            routeOverlay = null
        } else {
            routeOverlay = routeOverlay?.also { it.points = routePoints }
                ?: readyMap.addPolyline(
                    PolylineOptions().addAll(routePoints).color(Color.BLACK).width(ROUTE_WIDTH_PX),
                )
        }
        frameCamera(readyMap, routePoints + markerPosition)
    }

    private fun frameCamera(readyMap: HuaweiMap, visiblePoints: List<LatLng>) {
        cameraRunnable?.let(mapView::removeCallbacks)
        val distinctPoints = visiblePoints.distinctBy { it.latitude to it.longitude }
        cameraRunnable = Runnable {
            if (released || map !== readyMap) return@Runnable
            val update = if (distinctPoints.size >= 2) {
                val bounds = LatLngBounds.builder().apply { distinctPoints.forEach(::include) }.build()
                val padding = (CAMERA_PADDING_DP * mapView.resources.displayMetrics.density).roundToInt()
                CameraUpdateFactory.newLatLngBounds(bounds, padding)
            } else {
                CameraUpdateFactory.newLatLngZoom(marker.toHuaweiLatLng(), DEFAULT_ZOOM)
            }
            readyMap.moveCamera(update)
        }.also(mapView::post)
    }

    fun release() {
        if (released) return
        released = true
        cameraRunnable?.let(mapView::removeCallbacks)
        cameraRunnable = null
        map?.setOnMapClickListener(null)
        markerOverlay?.remove()
        routeOverlay?.remove()
        markerOverlay = null
        routeOverlay = null
        map = null
    }
}

private fun LatLongitude.toHuaweiLatLng() = LatLng(latitude, longitude)

private const val MAP_STATE_KEY_PREFIX = "huawei-map-view"
private const val DEFAULT_ZOOM = 14f
private const val CAMERA_PADDING_DP = 72
private const val ROUTE_WIDTH_PX = 8f
