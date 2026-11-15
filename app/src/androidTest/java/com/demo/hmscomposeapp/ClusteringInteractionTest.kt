package com.demo.hmscomposeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.hmscomposeapp.map.MapPerformanceProbe
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.*
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ClusteringInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun clusterItemExternalAndVehicleCallbacksKeepOrderConsumptionAndOwnership() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
        val origin = LatLng(24.7,46.67)
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(origin,16f))
        val marker = MarkerState(origin)
        var items by mutableStateOf(listOf(DemoStation(1,origin),DemoStation(2,origin)))
        var layer by mutableStateOf(true)
        var vehicle by mutableStateOf(false)
        var consume by mutableStateOf(false)
        var consumeExternal by mutableStateOf(false)
        val events = CopyOnWriteArrayList<String>()
        val id = AtomicReference<String?>(null)
        val ready = AtomicBoolean(false)
        MapPerformanceProbe.enabled = true; MapPerformanceProbe.reset()
        try {
            compose.setContent {
                GoogleMap(Modifier.fillMaxSize().testTag("cluster-map"),cameraPositionState = camera,
                    onMapLoaded = { ready.set(true) }) {
                    if (layer) AppClusteringWrapper(items,
                        onClusterClick = { events.add("cluster"); consume },
                        onClusterItemClick = { events.add("item"); consume },
                        onExternalItemClick = { events.add("external"); consumeExternal },
                        markerImageResId = R.drawable.demo_bus_pickup)
                    if (vehicle) Marker(state = marker, title = "Vehicle info", snippet = "Mixed marker",
                        onClick = { events.add("vehicle"); id.set(it.id); it.showInfoWindow(); true })
                }
            }
            compose.waitUntil(30_000) { ready.get() && MapPerformanceProbe.markerCreations >= 1 }
            tap(events)
            assertEquals(listOf("cluster","external"),events.toList())
            compose.runOnIdle { consume = true; events.clear() }
            tap(events)
            assertEquals(listOf("cluster"),events.toList())
            val count = MapPerformanceProbe.markerCreations
            compose.runOnIdle { consume = false; items = items.take(1); events.clear() }
            compose.waitUntil(10_000) { MapPerformanceProbe.markerCreations > count }
            tap(events)
            assertEquals(listOf("item","external"),events.toList())
            compose.runOnIdle { items = emptyList(); vehicle = true; events.clear() }
            compose.waitUntil(10_000) { MapPerformanceProbe.markerCreations > count+1 }
            tap(events)
            assertEquals(listOf("external","vehicle"),events.toList())
            val original = id.get()
            compose.runOnIdle {
                marker.hideInfoWindow()
                marker.position = LatLng(24.701,46.671)
                camera.move(CameraUpdateFactory.newLatLngZoom(marker.position,16f)); events.clear()
            }
            tap(events)
            assertEquals(original,id.get())
            compose.runOnIdle { marker.hideInfoWindow(); consumeExternal = true; events.clear() }
            tap(events)
            assertEquals(listOf("external"),events.toList())
            compose.runOnIdle { layer = false; events.clear() }
            tap(events)
            assertEquals(listOf("vehicle"),events.toList())
            // Remove a layer while a large grouping request is pending. The vehicle/map survive.
            compose.runOnIdle { layer = true; items = List(5000) { DemoStation(it,LatLng(24.71+it*.00001,46.68)) } }
            compose.runOnIdle { layer = false; events.clear(); marker.hideInfoWindow() }
            val afterRemoval = MapPerformanceProbe.markerCreations
            android.os.SystemClock.sleep(300)
            assertEquals(afterRemoval,MapPerformanceProbe.markerCreations)
            tap(events)
            assertEquals(listOf("vehicle"),events.toList())
        } finally { MapPerformanceProbe.enabled = false }
    }
    private fun tap(events: List<String>) {
        android.os.SystemClock.sleep(350) // Native marker updates are asynchronous to Compose.
        compose.onNodeWithTag("cluster-map").performTouchInput { click(center-Offset(0f,12f)) }
        compose.waitUntil(5_000) { events.isNotEmpty() }
        // A callback may append its first event before the main-thread dispatch chain returns.
        // Observe the completed chain, not a partial CopyOnWriteArrayList snapshot.
        compose.runOnIdle { }
    }
}
