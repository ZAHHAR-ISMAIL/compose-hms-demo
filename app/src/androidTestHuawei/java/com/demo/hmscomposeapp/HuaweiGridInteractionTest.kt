package com.demo.hmscomposeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.hmscomposeapp.map.MapPerformanceProbe
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.*
import com.demo.maps.compose.internal.HuaweiMapScene
import com.demo.maps.compose.internal.native
import com.demo.maps.compose.internal.scene
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Device coverage of the grid's adapter, using internal test access rather than a public raw map. */
class HuaweiGridInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeGroupMembersAndMarkerIdentitySurviveIdlePanAndViewportResize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
        val origin = LatLng(24.7, 46.67)
        val stations = List(3) { DemoStation(it, origin) }
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(origin, 16f))
        var width by mutableIntStateOf(320)
        val ready = AtomicBoolean(false)
        val activeScene = AtomicReference<HuaweiMapScene>()
        val clickedItems = AtomicReference<List<DemoStation>>()
        val clickedId = AtomicReference<String>()
        MapPerformanceProbe.enabled = true
        MapPerformanceProbe.reset()
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    GoogleMap(Modifier.width(width.dp).height(420.dp).testTag("grid-map"),
                        cameraPositionState = camera, onMapLoaded = { ready.set(true) }) {
                        val currentScene = scene()
                        SideEffect { activeScene.set(currentScene) }
                        AppClusteringWrapper(stations,
                            onClusterClick = { clickedItems.set(it.items.toList()); false },
                            onExternalItemClick = { clickedId.set(it.id); true },
                            markerImageResId = R.drawable.demo_bus_pickup)
                    }
                }
            }
            compose.waitUntil(30_000) { ready.get() && MapPerformanceProbe.markerCreations == 1L }
            fun tapGroup(): String {
                clickedItems.set(null)
                clickedId.set(null)
                // Native overlay updates are asynchronous to Compose. The map projection gives
                // the actual local touch point even after a pan or a smaller Android MapView.
                android.os.SystemClock.sleep(350)
                val point = compose.runOnIdle { activeScene.get().map.projection.toScreenLocation(origin.native()) }
                compose.onNodeWithTag("grid-map").performTouchInput {
                    click(Offset(point.x.toFloat(), point.y.toFloat() - 12f))
                }
                compose.waitUntil(5_000) { clickedId.get() != null }
                assertEquals(stations, clickedItems.get())
                return clickedId.get()
            }
            val originalId = tapGroup()
            compose.runOnIdle { camera.move(CameraUpdateFactory.scrollBy(40f, 0f)) }
            compose.waitUntil(10_000) { !camera.isMoving && camera.position.target != origin }
            assertEquals(originalId, tapGroup())
            val oldWidth = compose.runOnIdle { activeScene.get().host.mapView.width }
            compose.runOnIdle { width = 220 }
            compose.waitUntil(10_000) { activeScene.get().host.mapView.width < oldWidth }
            assertEquals(originalId, tapGroup())
            assertEquals("Panning/resizing must update retained markers", 1L, MapPerformanceProbe.markerCreations)
        } finally {
            MapPerformanceProbe.enabled = false
        }
    }
}
