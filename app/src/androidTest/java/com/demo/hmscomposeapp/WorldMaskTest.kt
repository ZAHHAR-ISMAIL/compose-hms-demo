package com.demo.hmscomposeapp

import android.graphics.Bitmap
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
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.*
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** Pixel regression: actual native renderer, existing world ring, synthetic region fixtures. */
class WorldMaskTest {
    @get:Rule val compose = createComposeRule()
    @Test fun worldMaskFillsExteriorAndLeavesRectangularAndConcaveHoles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
        val camera = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(24.7, 46.67), 12f))
        val ready = AtomicBoolean(false)
        var mapType by mutableStateOf(MapType.NORMAL)
        var show by mutableStateOf(false)
        var concave by mutableStateOf(false)
        var stroke by mutableFloatStateOf(0f)
        var overlay by mutableStateOf(false)
        var overlayZ by mutableFloatStateOf(4f)
        val clickedId = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val clickCount = java.util.concurrent.atomic.AtomicInteger(0)
        val world = listOf(LatLng(-85.0511,-180.0),LatLng(-85.0511,0.0),LatLng(-85.0511,179.9999),LatLng(85.0511,179.9999),LatLng(85.0511,0.0),LatLng(85.0511,-180.0))
        val rectangle = listOf(LatLng(24.68,46.65),LatLng(24.68,46.69),LatLng(24.72,46.69),LatLng(24.72,46.65))
        val notch = listOf(LatLng(24.68,46.65),LatLng(24.68,46.69),LatLng(24.72,46.69),LatLng(24.72,46.675),LatLng(24.695,46.675),LatLng(24.695,46.665),LatLng(24.72,46.665),LatLng(24.72,46.65))
        compose.setContent {
            GoogleMap(Modifier.fillMaxSize().testTag("mask-map"), cameraPositionState = camera, properties = MapProperties(mapType = mapType),
                uiSettings = MapUiSettings(zoomControlsEnabled = false), onMapLoaded = { ready.set(true) }) {
                if (show) Polygon(points = world, holes = listOf(if (concave) notch else rectangle), fillColor = Color.Magenta.copy(alpha = .5f), strokeWidth = stroke, strokeColor = Color.Red,
                    zIndex = 3f, clickable = true, tag = "mask", onClick = {
                        assertEquals("mask",it.tag)
                        val previousId = clickedId.getAndSet(it.id)
                        if (previousId != null) assertEquals("All native pieces share one identity",previousId,it.id)
                        clickCount.incrementAndGet()
                    })
                if (overlay) Polygon(points = listOf(LatLng(20.0,40.0),LatLng(20.0,50.0),LatLng(28.0,50.0),LatLng(28.0,40.0)),
                    fillColor = Color.Cyan,strokeWidth = 0f,zIndex = overlayZ)
            }
        }
        compose.waitUntil(30_000) { ready.get() }
        compose.runOnIdle { mapType = MapType.NONE }
        // The official Google updater runs in a map subcomposition. Wait for a native NONE
        // frame, rather than comparing a pending blank frame with the previous map tiles.
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try {
                val reference = frame.getPixel(frame.width/2,frame.height/2)
                (2..8).all { x -> (4..6).all { y -> delta(reference,frame.getPixel(frame.width*x/10,frame.height*y/10)) < 3 } }
            } finally { frame.recycle() }
        }
        val baseline = instrumentation.uiAutomation.takeScreenshot()
        val cx = baseline.width / 2
        val cy = baseline.height / 2
        val outsideX = baseline.width / 8
        compose.runOnIdle { show = true }
        var screenshot: Bitmap? = null
        compose.waitUntil(10_000) {
            screenshot?.recycle()
            screenshot = instrumentation.uiAutomation.takeScreenshot()
            delta(baseline.getPixel(outsideX, cy), screenshot!!.getPixel(outsideX, cy)) > 50 &&
                kotlin.math.abs(android.graphics.Color.green(screenshot!!.getPixel(outsideX,cy))-android.graphics.Color.green(baseline.getPixel(outsideX,cy))/2.0) < 8
        }
        val output = instrumentation.targetContext.getExternalFilesDir(null)!!
        output.resolve("mask-baseline.png").outputStream().use { baseline.compress(Bitmap.CompressFormat.PNG,100,it) }
        output.resolve("mask-rendered.png").outputStream().use { screenshot!!.compress(Bitmap.CompressFormat.PNG,100,it) }
        assertTrue("Hole must stay clear", delta(baseline.getPixel(cx,cy), screenshot!!.getPixel(cx,cy)) < 20)
        // A single 50% magenta layer changes green by approximately half, not full opacity.
        val green = android.graphics.Color.green(screenshot!!.getPixel(outsideX,cy))
        val before = android.graphics.Color.green(baseline.getPixel(outsideX,cy))
        assertEquals(before / 2.0, green.toDouble(), 8.0)
        for (y in baseline.height/4 until baseline.height*3/4) {
            assertEquals("Internal fill seams must not stack alpha", before/2.0,
                android.graphics.Color.green(screenshot!!.getPixel(outsideX,y)).toDouble(), 10.0)
        }
        screenshot!!.recycle()
        compose.onNodeWithTag("mask-map").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x/4f,center.y)) }
        compose.waitUntil(5_000) { clickedId.get() != null }
        compose.runOnIdle { stroke = 4f }
        try { compose.waitUntil(5_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try {
                val red = (0 until frame.width).map { x -> val pixel = frame.getPixel(x,cy)
                    android.graphics.Color.red(pixel)>230 && android.graphics.Color.green(pixel)<50 && android.graphics.Color.blue(pixel)<50 }
                val runs = red.indices.count { red[it] && (it==0 || !red[it-1]) }
                runs == 2 && red.count { it } in 2..frame.width/10 // Two original boundaries, no internal strokes.
            } finally { frame.recycle() }
        } } catch (failure: Throwable) {
            val frame=instrumentation.uiAutomation.takeScreenshot()
            output.resolve("mask-stroke.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
            frame.recycle(); throw failure
        }
        compose.runOnIdle { concave = true; stroke = 0f }
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try { delta(baseline.getPixel(cx,cy), frame.getPixel(cx,cy)) > 50 } finally { frame.recycle() }
        }
        compose.runOnIdle { camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(24.7,46.67),13f)) }
        compose.waitUntil(5_000) { kotlin.math.abs(camera.position.zoom-13f)<.1f }
        compose.onNodeWithTag("mask-map").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x*1.75f,center.y)) }
        compose.waitUntil(5_000) { clickCount.get() >= 2 }
        compose.runOnIdle { overlay = true }
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try { android.graphics.Color.green(frame.getPixel(outsideX,cy))>245 } finally { frame.recycle() }
        }
        compose.runOnIdle { overlayZ = 2f }
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try { android.graphics.Color.green(frame.getPixel(outsideX,cy)) in 120..135 } finally { frame.recycle() }
        }
        compose.runOnIdle { show = false; overlay = false }
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            try { delta(baseline.getPixel(cx,cy), frame.getPixel(cx,cy)) < 20 } finally { frame.recycle() }
        }
        baseline.recycle()
    }
    private fun delta(a: Int, b: Int) = kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b)) +
        kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b)) + kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
}
