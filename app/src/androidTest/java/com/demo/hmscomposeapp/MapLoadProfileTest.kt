package com.demo.hmscomposeapp

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.hmscomposeapp.busondemand.LiveBusTransportMarker
import com.demo.hmscomposeapp.map.MapPerformanceProbe
import com.demo.maps.MapsInitializer
import com.demo.maps.compose.*
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded synthetic workload, measured with wall-clock updates rather than Compose's test clock. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 31)
class MapLoadProfileTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun profileStationsAndTwentyVehicles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
        val worker = HandlerThread("MapFrameMetrics").apply { start() }
        val handler = Handler(Looper.getMainLooper())
        val durations = Collections.synchronizedList(mutableListOf<Double>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1_000_000.0)
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper)) }
        var stations by mutableIntStateOf(200)
        var tick by mutableIntStateOf(0)
        val ready = AtomicBoolean(false)
        var updating = false
        val update = object : Runnable {
            override fun run() { if (updating) { tick++; handler.postDelayed(this,50) } }
        }
        MapPerformanceProbe.enabled = true
        try {
            compose.setContent {
                val items = remember(stations) { List(stations) { DemoStation(it,LatLng(24.7+(it/40-10)*.001,46.67+(it%40-20)*.001)) } }
                val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(24.7,46.67),13f) }
                GoogleMap(Modifier.fillMaxSize(),cameraPositionState = camera,onMapLoaded = { ready.set(true) }) {
                    AppClusteringWrapper(items = items,markerImageResId = R.drawable.demo_bus_pickup)
                    repeat(20) { vehicle -> key(vehicle) {
                        // Four vehicles change bearing/icons at 20 Hz; sixteen move with static visuals.
                        LiveBusTransportMarker(LatLng(24.7+vehicle*.0005,46.67+kotlin.math.sin(tick/50.0)*.005),
                            iconResId = R.drawable.demo_live_bus,isAtStop = false,
                            bearing = if (vehicle < 4) (tick%360).toFloat() else 90f)
                    } }
                }
            }
            compose.waitUntil(30_000) { ready.get() }
            val reports = mutableListOf<String>()
            for (count in listOf(200,1000)) {
                compose.runOnIdle { stations = count }
                val warmup = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime()-warmup<1000) { compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(16) }
                val surface = findMapSurface()
                val oldPresentations = surface?.let(::presentationTimes).orEmpty().toSet()
                val nativePresentations = mutableSetOf<Long>()
                var lastSurfacePoll = 0L
                val before = allocatedBytes()
                durations.clear()
                compose.runOnUiThread { MapPerformanceProbe.reset(); MapPerformanceProbe.enabled = true; updating = true; handler.post(update) }
                val started = SystemClock.elapsedRealtime()
                // Compose tests own their frame clock. Advance it at wall-clock pace so this
                // measures rendered updates rather than accumulating state behind a paused clock.
                while (SystemClock.elapsedRealtime()-started < 5000) {
                    compose.mainClock.advanceTimeByFrame()
                    SystemClock.sleep(16)
                    if (surface != null && SystemClock.elapsedRealtime()-lastSurfacePoll>500) {
                        nativePresentations.addAll(presentationTimes(surface).filter { it !in oldPresentations })
                        lastSurfacePoll=SystemClock.elapsedRealtime()
                    }
                }
                val elapsed = SystemClock.elapsedRealtime()-started
                compose.runOnUiThread { updating = false; handler.removeCallbacks(update) }
                val frames = synchronized(durations) { durations.toList().sorted() }
                fun percentile(fraction: Double) = if (frames.isEmpty()) 0.0 else frames[((frames.size-1)*fraction).toInt()]
                val nativeIntervals = nativePresentations.sorted().zipWithNext { a,b -> (b-a)/1_000_000.0 }.sorted()
                fun nativePercentile(fraction: Double) = if (nativeIntervals.isEmpty()) "unavailable" else nativeIntervals[((nativeIntervals.size-1)*fraction).toInt()].toString()
                if (frames.isEmpty() && nativeIntervals.size<=10) instrumentation.sendStatus(0,android.os.Bundle().apply {
                    putString("stream","\nProfile diagnostic: surface=$surface bitmapFrames=${MapPerformanceProbe.bitmapFrames} nativeFrames=${nativePresentations.size} raw=${surface?.let { shell("dumpsys SurfaceFlinger --latency '"+it+"'").takeLast(400) }}\n")
                })
                org.junit.Assert.assertTrue("Profile must contain actual rendered frames",frames.isNotEmpty() || nativeIntervals.size>10)
                org.junit.Assert.assertTrue("Profile must redraw the four rotating vehicles",MapPerformanceProbe.bitmapFrames > 0)
                val provider = instrumentation.targetContext.getString(R.string.map_provider_name)
                reports.add("provider=$provider stations=$count vehicles=20 durationMs=$elapsed windowFrames=${frames.size} windowP50Ms=${if (frames.isEmpty()) "unavailable" else percentile(.5)} windowP95Ms=${if (frames.isEmpty()) "unavailable" else percentile(.95)} nativePresentations=${nativePresentations.size} nativeIntervalP50Ms=${nativePercentile(.5)} nativeIntervalP95Ms=${nativePercentile(.95)} allocatedBytes=${allocatedBytes()-before} bitmapFrames=${MapPerformanceProbe.bitmapFrames} iconRequests=${MapPerformanceProbe.iconRequests} markerCreations=${MapPerformanceProbe.markerCreations}")
            }
            val report = reports.joinToString("\n",postfix="\n")
            instrumentation.targetContext.getExternalFilesDir(null)!!.resolve("map-load-profile.txt").writeText(report)
            instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\n$report") })
        } finally {
            compose.runOnUiThread {
                updating = false; handler.removeCallbacks(update); MapPerformanceProbe.enabled = false
                compose.activity.window.removeOnFrameMetricsAvailableListener(listener)
            }
            worker.quitSafely()
        }
    }
    // UiAutomation's direct command execution does not parse shell quotes. Feed an actual shell
    // over stdin so SurfaceFlinger layer names containing spaces remain one safely quoted argument.
    @android.annotation.TargetApi(31)
    private fun shell(command: String): String {
        val descriptors = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("sh")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write((command+"\nexit\n").toByteArray()) }
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).bufferedReader().use { it.readText() }
    }
    private fun findMapSurface(): String? = shell("dumpsys SurfaceFlinger --list").lineSequence()
        .filter { "SurfaceView[com.demo.hmscomposeapp/" in it && "Background" !in it && "animation-leash" !in it }
        .map { it.removePrefix("RequestedLayerState{").substringBefore(" parentId=").removeSuffix("}") }
        .sortedWith(compareByDescending<String> { "ComponentActivity" in it }.thenByDescending { Regex("#(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull() ?: 0L }).firstOrNull()
    private fun presentationTimes(surface: String): List<Long> {
        val quoted = "'"+surface.replace("'", "'\"'\"'")+"'"
        return shell("dumpsys SurfaceFlinger --latency $quoted").lineSequence().mapNotNull { line ->
            val values=line.trim().split(Regex("\\s+"))
            if (values.size!=3) null else values[1].toLongOrNull()?.takeIf { it>0 && it<Long.MAX_VALUE }
        }.toList()
    }
    private fun allocatedBytes() = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
}
