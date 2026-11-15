package com.demo.hmscomposeapp.busondemand

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.withRotation
import com.demo.hmscomposeapp.R
import com.demo.hmscomposeapp.map.vectorBitmap
import com.demo.maps.compose.GoogleMapComposable
import com.demo.maps.compose.Marker
import com.demo.maps.compose.rememberUpdatedMarkerState
import com.demo.maps.model.BitmapDescriptorFactory
import com.demo.maps.model.LatLng
import kotlinx.coroutines.delay

/** Internal timing seam: tests can step frames; the public production helper signature is intact. */
internal class RippleTiming(val enabled: Boolean = true, val nextFrame: suspend () -> Unit = { delay(50) })
internal val LocalRippleTiming = staticCompositionLocalOf { RippleTiming() }
internal class MarkerRenderMetrics {
    var bitmaps = 0
    var descriptors = 0
}
internal val LocalMarkerRenderMetrics = staticCompositionLocalOf<MarkerRenderMetrics?> { null }

/** Supplied canvas marker, with stable state and input-aware bitmap invalidation. */
@Composable
@GoogleMapComposable
fun LiveBusTransportMarker(
    markerPosition: LatLng,
    isVisible: Boolean = true,
    @DrawableRes iconResId: Int,
    isAtStop: Boolean = true,
    rippleColorSolid: Color = Color(0xFF3A81F6),
    bearing: Float = 0f,
    isBod: Boolean = false,
) {
    if (!isVisible) return
    val context = LocalContext.current
    val state = rememberUpdatedMarkerState(markerPosition) // One native marker throughout movement.
    val base = remember(context, iconResId) { vectorBitmap(context, iconResId) }
    // Neutral tracker artwork replaces the unavailable production vector; geometry is unchanged.
    val tracker = remember(context, rippleColorSolid) { vectorBitmap(context, R.drawable.demo_tracker, rippleColorSolid.toArgb()) }
    val timing = LocalRippleTiming.current
    val metrics = LocalMarkerRenderMetrics.current
    var frame by remember(isAtStop, timing) { mutableIntStateOf(0) }
    LaunchedEffect(isAtStop, timing) {
        if (isAtStop && timing.enabled) while (true) {
            timing.nextFrame()
            frame = (frame + 1) % 50 // 50 ms x 50 frames = the supplied 2.5-second cycle.
        }
    }
    val progress = if (isAtStop) frame / 50f else 0f
    // Position changes do not invalidate the image. An unchanged non-ripple vehicle allocates
    // no new image/descriptor. Every published bitmap is immutable from this point onward;
    // neither SDK is assumed to have finished reading a previously supplied bitmap.
    val icon = remember(base, tracker, rippleColorSolid, bearing, isBod, isAtStop, progress) {
        val bitmap = drawBusMarker(base, tracker, rippleColorSolid.copy(alpha = .3f).toArgb(), progress, isAtStop, bearing, isBod)
        metrics?.let { it.bitmaps++; it.descriptors++ }
        com.demo.hmscomposeapp.map.MapPerformanceProbe.bitmap()
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    Marker(state = state, icon = icon, anchor = Offset(.5f, .5f), zIndex = 1f)
}

/** Pixel geometry and the unusual zero-bearing offset intentionally match the supplied helper. */
internal fun drawBusMarker(base: Bitmap, tracker: Bitmap, rippleColor: Int, progress: Float,
    showRipple: Boolean, bearing: Float, isBod: Boolean): Bitmap {
    val maxRadius = base.width * 1.5f
    val radius = (maxRadius - base.width / 1.4f) * progress
    val size = (maxRadius * 2).toInt()
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val x = size / 2f
        val y = size / 2f
        if (showRipple) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rippleColor; style = Paint.Style.STROKE }
            paint.strokeWidth = 40f; paint.alpha = (150 * (1 - progress)).toInt()
            canvas.drawCircle(x, y, radius, paint)
            paint.strokeWidth = 30f; paint.alpha = (255 * (1 - progress)).toInt()
            if (progress > 0) canvas.drawCircle(x, y, radius * .8f, paint)
        }
        canvas.drawBitmap(base, x - base.width / 2f, y - base.height / 2f, null)
        canvas.withRotation(bearing + if (bearing != 0f) 45 else 90, x, y) {
            val factor = if (isBod) 1.8f else 1.5f
            drawBitmap(tracker, x - tracker.width * factor, y - tracker.height * factor, null)
        }
    }
}
