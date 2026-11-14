package com.demo.maps.compose.internal

import com.demo.maps.CameraUpdate
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import com.huawei.hms.maps.HuaweiMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.huawei.hms.maps.CameraUpdateFactory as NativeUpdates
import com.huawei.hms.maps.model.BitmapDescriptorFactory as NativeIcons

/**
 * Execute shared camera commands against one native HMS map. HuaweiMapScene creates this after
 * getMapAsync; shared CameraPositionState attaches it only once the host has non-zero dimensions.
 *
 * move is immediate. animate must suspend until the native SDK reports completion or cancellation,
 * so a screen's existing `cameraPositionState.animate(...)` coroutine has meaningful lifecycle.
 * Normal map mutations are expected on the UI thread, as required by the shared backend contract.
 */
internal class HuaweiCameraController(private val map: HuaweiMap, private val renderingReady: Deferred<Unit>) : CameraController {
    // Each new command invalidates the old animation's token. Its delayed cancellation cleanup
    // must not stop a newer animation that has already taken control of the same native camera.
    private var generation = 0L
    override fun move(update: CameraUpdate) {
        stopAnimation()
        map.moveCamera(update.native())
    }
    override suspend fun animate(update: CameraUpdate, durationMs: Int) {
        // HMS may cancel an animation submitted during renderer startup without finishing it.
        // Initial move commands can set the viewport immediately; animations await the first frame.
        // This wait is cancellable too: leaving the screen does not leave a pending animation alive.
        renderingReady.await()
        val current = ++generation
        var completed = false
        try {
            // Turn the SDK's callback API into the suspending API expected by shared screen code.
            suspendCancellableCoroutine<Unit> { continuation ->
                val callback = object : HuaweiMap.CancelableCallback {
                    override fun onFinish() {
                        completed = true
                        // A late native callback may arrive after the caller cancelled its job.
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCancel() {
                        // An interrupted animation is cancellation, not a successful arrival.
                        if (continuation.isActive) continuation.cancel(CancellationException("HMS camera animation interrupted"))
                    }
                }
                // The shared API uses Int.MAX_VALUE to request the SDK's default duration. Passing
                // that sentinel as a literal millisecond duration would be a very long animation.
                if (durationMs == Int.MAX_VALUE) map.animateCamera(update.native(), callback)
                else map.animateCamera(update.native(), durationMs, callback)
            }
        } finally {
            // Coroutine cancellation must stop native motion as well. NonCancellable lets cleanup
            // run after job cancellation, and Main keeps the SDK call on its required thread.
            // The generation check protects a newer command from this older request's cleanup.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (!completed && generation == current) stopAnimation()
            }
        }
    }
    override fun stopAnimation() { generation++; map.stopAnimation() }
}

// Explicit HMS boundary conversions. This flavor's compatible LatLng is not a native HMS LatLng;
// callbacks copy native coordinates back into its model. Google uses native aliases instead.
internal fun LatLng.native() = com.huawei.hms.maps.model.LatLng(latitude, longitude)
internal fun com.huawei.hms.maps.model.LatLng.shared() = LatLng(latitude, longitude)
internal fun LatLngBounds.native() = com.huawei.hms.maps.model.LatLngBounds(southwest.native(), northeast.native())
internal fun com.huawei.hms.maps.model.CameraPosition.shared() = CameraPosition(target.shared(), zoom, tilt, bearing)
private fun CameraPosition.native() = com.huawei.hms.maps.model.CameraPosition(target.native(), zoom, tilt, bearing)
// Translate only when executing a command, so a screen can create commands before SDK startup.
// Bounds with explicit dimensions use that overload; otherwise host layout supplies the dimensions.
private fun CameraUpdate.native(): com.huawei.hms.maps.CameraUpdate = when (this) {
    is CameraUpdate.Position -> NativeUpdates.newCameraPosition(position.native())
    is CameraUpdate.Target -> zoom?.let { NativeUpdates.newLatLngZoom(target.native(), it) } ?: NativeUpdates.newLatLng(target.native())
    is CameraUpdate.Bounds -> if (width != null && height != null) NativeUpdates.newLatLngBounds(bounds.native(), width!!, height!!, padding)
        else NativeUpdates.newLatLngBounds(bounds.native(), padding)
    is CameraUpdate.Scroll -> NativeUpdates.scrollBy(xPixel, yPixel)
    is CameraUpdate.Zoom -> if (absolute) NativeUpdates.zoomTo(amount) else focus?.let { NativeUpdates.zoomBy(amount, it) } ?: NativeUpdates.zoomBy(amount)
}
// A descriptor describes an image/resource/default pin without tying screen code to the HMS class.
// ComposeMarkerRenderer supplies Image values for custom Compose markers through this same path.
internal fun BitmapDescriptor.native(): com.huawei.hms.maps.model.BitmapDescriptor = when (this) {
    is BitmapDescriptor.Image -> NativeIcons.fromBitmap(bitmap)
    is BitmapDescriptor.Resource -> NativeIcons.fromResource(resourceId)
    is BitmapDescriptor.Default -> NativeIcons.defaultMarker(hue)
}
