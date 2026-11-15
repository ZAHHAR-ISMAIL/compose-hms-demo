package com.demo.hmscomposeapp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.maps.MapsInitializer
import com.demo.maps.CameraUpdateFactory
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MarkerComposable
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.Polygon
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import com.demo.maps.model.MapStyleOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Native renderer tests, shared unchanged between google and huawei instrumentation variants. */
class MapCompatibilityTest {
    @get:Rule val compose = createComposeRule()
    private val origin = LatLng(24.713630, 46.675293)
    private val destination = LatLng(24.710543, 46.676544)

    @Before fun initializeNativeSdk() {
        // CameraUpdateFactory is the real Google factory on that flavor. It needs SDK setup even
        // when testing a command issued before a MapView exists. HMS also initializes explicitly.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
    }

    @Test fun cameraCommandsBeforeReadinessAndAnimationCompletion() {
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(origin, 14f))
        camera.move(CameraUpdateFactory.newLatLngZoom(origin, 15f))
        lateinit var scope: CoroutineScope
        val loaded = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        compose.setContent {
            scope = rememberCoroutineScope()
            GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera, onMapLoaded = { loaded.set(true) })
        }
        compose.runOnIdle {
            scope.launch {
                try { camera.animate(CameraUpdateFactory.newLatLngZoom(destination, 16f), 250); completed.set(true) }
                catch (exception: Throwable) { failure.set(exception) }
            }
        }
        compose.waitUntil(30_000) { completed.get() || failure.get() != null }
        assertNull("Camera animation failed before completing", failure.get())
        compose.runOnIdle {
            assertEquals(destination.latitude, camera.position.target.latitude, 0.0001)
            assertEquals(destination.longitude, camera.position.target.longitude, 0.0001)
            assertEquals(16f, camera.position.zoom, 0.15f)
        }
        compose.waitUntil(30_000) { loaded.get() }
    }

    @Test fun disposalCancelsNativeAnimationAndStateCanBeReused() {
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(origin, 15f))
        var showing by mutableStateOf(true)
        val loaded = AtomicInteger(0)
        lateinit var scope: CoroutineScope
        lateinit var animation: Job
        compose.setContent {
            scope = rememberCoroutineScope()
            if (showing) GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera, onMapLoaded = { loaded.incrementAndGet() })
        }
        compose.waitUntil(30_000) { loaded.get() >= 1 }
        compose.runOnIdle { animation = scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(destination, 18f), 10_000) } }
        compose.waitUntil(10_000) { camera.isMoving }
        compose.runOnIdle { showing = false }
        compose.waitUntil(5_000) { animation.isCancelled }
        compose.runOnIdle { showing = true }
        compose.waitUntil(30_000) { loaded.get() >= 2 }
        // Tiles/map loading and camera-idle are independent native callbacks. Reattaching the
        // state issues an initial move, which may still be settling when onMapLoaded fires.
        compose.waitUntil(5_000) { !camera.isMoving }
        compose.runOnIdle { assertFalse(camera.isMoving) }
    }

    @Test fun customMarkerRefreshesAndMovingMarkerKeepsNativeIdentity() {
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(origin, 16f))
        val marker = MarkerState(origin)
        var markerColor by mutableStateOf(Color.Magenta)
        var showing by mutableStateOf(true)
        val clicked = AtomicReference<String?>(null)
        val loaded = AtomicBoolean(false)
        compose.setContent {
            GoogleMap(Modifier.fillMaxSize().testTag("native-map"), cameraPositionState = camera, onMapLoaded = { loaded.set(true) }) {
                Polygon(points = listOf(LatLng(24.72, 46.67), LatLng(24.70, 46.67), LatLng(24.70, 46.69), LatLng(24.72, 46.69)),
                    holes = listOf(listOf(LatLng(24.716, 46.674), LatLng(24.710, 46.674), LatLng(24.710, 46.680), LatLng(24.716, 46.680))),
                    fillColor = Color.Black.copy(alpha = 0.2f), strokeWidth = 0f)
                if (showing) key("bus") {
                    MarkerComposable(markerColor, state = marker, anchor = Offset(0.5f, 0.5f), onClick = { clicked.set(it.id); true }) {
                        Box(Modifier.size(40.dp).background(markerColor))
                    }
                }
            }
        }
        compose.waitUntil(30_000) { loaded.get() }
        waitForColor(magenta = true, present = true)
        compose.onNodeWithTag("native-map").performTouchInput { click(center) }
        compose.waitUntil(5_000) { clicked.get() != null }
        val originalId = clicked.get()
        compose.runOnIdle {
            markerColor = Color.Green
            marker.position = destination
            camera.move(CameraUpdateFactory.newLatLngZoom(destination, 16f))
            clicked.set(null)
        }
        waitForColor(magenta = false, present = true)
        compose.onNodeWithTag("native-map").performTouchInput { click(center) }
        compose.waitUntil(5_000) { clicked.get() != null }
        assertEquals(originalId, clicked.get())
        compose.runOnIdle { showing = false }
        waitForColor(magenta = false, present = false)
    }

    @Test fun multipleMapsAndFlavorSpecificStylesRemainIndependent() {
        val first = CameraPositionState(CameraPosition.fromLatLngZoom(origin, 15f))
        val second = CameraPositionState(CameraPosition.fromLatLngZoom(destination, 15f))
        val loaded = AtomicInteger(0)
        var dark by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                GoogleMap(Modifier.weight(1f), cameraPositionState = first,
                    properties = MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.dark_map_style else R.raw.map_style)),
                    onMapLoaded = { loaded.incrementAndGet() })
                GoogleMap(Modifier.weight(1f), cameraPositionState = second, onMapLoaded = { loaded.incrementAndGet() })
            }
        }
        compose.waitUntil(30_000) { loaded.get() >= 2 }
        compose.runOnIdle { dark = true; first.move(CameraUpdateFactory.newLatLngZoom(LatLng(24.72, 46.68), 17f)) }
        compose.waitUntil(5_000) { kotlin.math.abs(first.position.target.latitude - 24.72) < 0.0001 }
        compose.runOnIdle {
            assertEquals(destination.latitude, second.position.target.latitude, 0.0001)
            assertEquals(destination.longitude, second.position.target.longitude, 0.0001)
        }
        compose.runOnIdle { dark = false }
    }

    private fun waitForColor(magenta: Boolean, present: Boolean) {
        compose.waitUntil(20_000) {
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try { (countMarkerPixels(screenshot, magenta) > 500) == present } finally { screenshot.recycle() }
        }
    }
    private fun countMarkerPixels(bitmap: Bitmap, magenta: Boolean): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count {
            val red = android.graphics.Color.red(it)
            val green = android.graphics.Color.green(it)
            val blue = android.graphics.Color.blue(it)
            if (magenta) red > 230 && green < 30 && blue > 230 else red < 30 && green > 230 && blue < 30
        }
    }
}
