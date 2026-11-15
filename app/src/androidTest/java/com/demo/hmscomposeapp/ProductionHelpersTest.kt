package com.demo.hmscomposeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.demo.maps.CameraUpdateFactory
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.hmscomposeapp.busondemand.*
import com.demo.hmscomposeapp.map.*
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.*
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductionHelpersTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun stopFollowsCoordinateChangesWithoutReplacingItsNativeMarker() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { MapsInitializer.initialize(context) }
        var point by mutableStateOf(LatLng(24.7,46.67))
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(point,16f))
        val ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val id = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val clicks = java.util.concurrent.atomic.AtomicInteger(0)
        compose.setContent {
            GoogleMap(Modifier.fillMaxSize().testTag("stop-map"),cameraPositionState = camera,onMapLoaded = { ready.set(true) }) {
                AppClusteringWrapper(items = emptyList<DemoStation>(),onExternalItemClick = { id.set(it.id); false },markerImageResId = R.drawable.demo_bus_pickup)
                BusStopMarker(title = "Demo stop",latPosition = point,visible = true) { clicks.incrementAndGet() }
            }
        }
        compose.waitUntil(30_000) { ready.get() }
        compose.onNodeWithTag("stop-map").performTouchInput { click(center) }
        compose.waitUntil(5_000) { clicks.get()==1 }
        val original = id.get()
        compose.runOnIdle { point=LatLng(24.701,46.671); camera.move(CameraUpdateFactory.newLatLngZoom(point,16f)); id.set(null) }
        android.os.SystemClock.sleep(350)
        compose.onNodeWithTag("stop-map").performTouchInput { click(center) }
        compose.waitUntil(5_000) { clicks.get()==2 }
        assertNotNull(original)
        assertEquals(original,id.get())
    }

    @Test fun busCachesStaticVisualsRefreshesInputsAndStepsRippleDeterministically() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { MapsInitializer.initialize(context) }
        val metrics = MarkerRenderMetrics()
        val frames = Channel<Unit>(Channel.UNLIMITED)
        val timing = RippleTiming(nextFrame = { frames.receive() })
        var position by mutableStateOf(LatLng(24.7,46.67))
        var stopped by mutableStateOf(false)
        var bearing by mutableFloatStateOf(0f)
        var tint by mutableStateOf(Color.Blue)
        var mode by mutableStateOf(false)
        var resource by mutableIntStateOf(R.drawable.demo_live_bus)
        var visible by mutableStateOf(true)
        val nativeId = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(position,15f))
        compose.setContent {
            CompositionLocalProvider(LocalRippleTiming provides timing, LocalMarkerRenderMetrics provides metrics) {
                GoogleMap(Modifier.fillMaxSize().testTag("real-bus"), cameraPositionState = camera, onMapLoaded = { loaded.set(true) }) {
                    AppClusteringWrapper(items = emptyList<DemoStation>(), onExternalItemClick = { nativeId.set(it.id); true }, markerImageResId = R.drawable.demo_bus_pickup)
                    LiveBusTransportMarker(position, visible, resource, stopped, tint, bearing, mode)
                }
            }
        }
        compose.waitUntil(30_000) { metrics.bitmaps > 0 }
        compose.waitUntil(30_000) { loaded.get() }
        compose.onNodeWithTag("real-bus").performTouchInput { click(center) }
        compose.waitUntil(5_000) { nativeId.get()!=null }
        val originalId = nativeId.get()
        compose.runOnIdle {
            assertEquals(1,metrics.bitmaps); position = LatLng(24.701,46.67)
            camera.move(CameraUpdateFactory.newLatLngZoom(position,15f)); nativeId.set(null)
        }
        android.os.SystemClock.sleep(350)
        compose.onNodeWithTag("real-bus").performTouchInput { click(center) }
        compose.waitUntil(5_000) { nativeId.get()!=null }
        assertEquals("Real bus retains native identity",originalId,nativeId.get())
        compose.runOnIdle { assertEquals("Position-only updates must not redraw",1,metrics.bitmaps); bearing = 90f }
        compose.runOnIdle { assertEquals(2,metrics.bitmaps); tint = Color.Red }
        compose.runOnIdle { assertEquals(3,metrics.bitmaps); mode = true }
        compose.runOnIdle { assertEquals(4,metrics.bitmaps); resource = R.drawable.demo_bus_pickup }
        compose.runOnIdle { assertEquals(5,metrics.bitmaps); stopped = true }
        compose.runOnIdle { assertEquals(6,metrics.bitmaps) }
        repeat(50) { index ->
            frames.trySend(Unit)
            compose.waitUntil(5_000) { metrics.bitmaps == index + 7 }
        }
        compose.runOnIdle { stopped = false }
        compose.runOnIdle {
            val before = metrics.bitmaps
            position = LatLng(24.702,46.67)
            assertEquals(metrics.descriptors,metrics.bitmaps)
            assertEquals(57,before)
        }
        compose.runOnIdle { assertEquals(57,metrics.bitmaps); visible = false }
        frames.trySend(Unit)
        compose.runOnIdle { assertEquals(57,metrics.bitmaps) }
    }

    @Test fun ripplePixelsPreserveSizeBearingAndStoppedGeometry() {
        val base = vectorBitmap(context,R.drawable.demo_live_bus)
        val tracker = vectorBitmap(context,R.drawable.demo_tracker,android.graphics.Color.BLUE)
        fun image(progress: Float, ripple: Boolean, bearing: Float = 0f, mode: Boolean = false) =
            drawBusMarker(base,tracker,0x4D3A81F6,progress,ripple,bearing,mode)
        val still = image(0f,false)
        assertEquals(base.width*3,still.width)
        assertTrue(still.sameAs(image(.8f,false)))
        assertTrue(image(.4f,true).sameAs(image(.4f,true)))
        assertFalse(still.sameAs(image(.4f,true)))
        assertFalse(still.sameAs(image(0f,false,90f)))
        assertFalse(still.sameAs(image(0f,false,mode=true)))
        assertEquals(0,android.graphics.Color.alpha(still.getPixel(0,0)))
    }

    @Test fun boundariesAreCachedAcrossProvidersAndMalformedDataIsRejected() {
        RegionBoundsLocalProvider.clearCacheForTests()
        val first = RegionBoundsLocalProvider(context)
        val second = RegionBoundsLocalProvider(context)
        assertSame(first.getCoordinatesPair(),second.getCoordinatesPair())
        val bounds = second.getRiyadhLatLngBounds()
        first.getCoordinatesPair().riyadh.forEach { assertTrue(bounds.contains(it)) }
        val source = context.assets.open("synthetic_region_boundaries.json").bufferedReader().use { it.readText() }
        assertEquals(5,parseBoundaries(source).riyadh.size)
        for (bad in listOf("{}",source.replace("Polygon","MultiPolygon"),source.replace("[[46.66,24.725]", "[]"),
            "{\"type\":\"FeatureCollection\",\"features\":[]}",
            source.replace("24.675","124.675"))) {
            assertTrue(runCatching { parseBoundaries(bad) }.isFailure)
        }
        val emptyRegion = org.json.JSONObject(source).apply {
            getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").put(1,org.json.JSONArray())
        }.toString()
        assertTrue(runCatching { parseBoundaries(emptyRegion) }.isFailure)
        assertTrue(runCatching { vectorBitmap(context,android.R.color.transparent) }.isFailure)
    }
}
