package com.demo.maps.compose.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.createBitmap
import com.demo.maps.model.BitmapDescriptor

/**
 * Implement the UI-content part of shared MarkerComposable for HMS, which accepts a bitmap icon
 * rather than a Compose lambda. A retained attached ComposeView draws the content; the returned
 * observable descriptor causes MarkerNode to update its icon when that image changes.
 *
 * The renderer follows the map scene's lifetime. It tracks content/visual keys rather than marker
 * coordinates: moving or rotating a bus is handled by MarkerNode and should not redraw a static icon.
 */
@Composable
internal fun rememberMarkerIcon(scene: HuaweiMapScene, keys: Array<out Any>, content: @Composable () -> Unit): State<BitmapDescriptor?> {
    // The offscreen ComposeView still needs the screen's theme/density/CompositionLocals. Its
    // parent composition carries those values even though it draws into a bitmap rather than UI.
    val parentComposition = rememberCompositionContext()
    // The retained view reads the latest lambda without being recreated for each recomposition.
    val latestContent = rememberUpdatedState(content)
    val result = remember(scene) { mutableStateOf<BitmapDescriptor?>(null) }
    val renderer = remember(scene, parentComposition) {
        ComposeMarkerRenderer(scene.host, parentComposition) { result.value = it }
    }
    // Install once per renderer. Removing the marker/map disposes the auxiliary composition and
    // cancels its scheduled draw, so navigation cannot keep rendering icons for a dead native map.
    DisposableEffect(renderer) {
        renderer.install {
            latestContent.value()
            // A successful content recomposition can change the pixels even at the same size.
            SideEffect { renderer.requestRender() }
        }
        onDispose { renderer.dispose() }
    }
    SideEffect { renderer.updateContent(keys.toList(), content) }
    return result
}

/** Owns one marker's offscreen view and reusable bitmap; SDK icon upload remains in MarkerNode. */
private class ComposeMarkerRenderer(
    private val parent: HuaweiMapHostView,
    compositionContext: CompositionContext,
    private val onBitmap: (BitmapDescriptor) -> Unit,
) {
    private var disposed = false
    private var scheduled = false
    private var previousKeys: List<Any>? = null
    private var previousContent: Any? = null
    private var bitmap: Bitmap? = null
    private val container = IconContainer(parent).apply {
        // Keep the ComposeView attached for lifecycle/state ownership and measurement, but place
        // this drawing source outside the visible map. The user sees the SDK marker, not this view.
        translationX = -100_000f
        // Avoid duplicate accessibility nodes for the offscreen copy of marker content.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        onInvalidated = ::requestRender
    }
    private val composeView = ComposeView(parent.context).apply {
        setParentCompositionContext(compositionContext)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    // The queued callback can outlive the event that requested it. Both the renderer and native
    // host must still be alive when it runs, particularly during navigation or Activity teardown.
    private val render = Runnable {
        scheduled = false
        if (!disposed && !parent.released) renderBitmap()
    }

    /** Attach before measurement/composition so the view can resolve its Android tree owners. */
    fun install(content: @Composable () -> Unit) {
        container.addView(composeView)
        parent.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        composeView.setContent(content)
        requestRender()
    }
    /** Visual keys are for changes such as icon/status; native position is intentionally separate. */
    fun updateContent(keys: List<Any>, content: Any) {
        if (keys != previousKeys || content !== previousContent) {
            previousKeys = keys
            previousContent = content
            requestRender()
        }
    }
    /** Coalesce multiple invalidations into one queued draw and prevent recursive self-redraws. */
    fun requestRender() {
        if (disposed || scheduled || container.drawingBitmap || parent.released) return
        scheduled = true
        // Use the UI frame scheduler rather than drawing immediately from a composition callback.
        parent.postOnAnimation(render)
    }
    private fun renderBitmap() {
        // Measuring/layout/drawing may themselves invalidate child views. Suppress those callbacks
        // during our manual draw, or each generated icon could schedule another draw indefinitely.
        container.drawingBitmap = true
        try {
            // Measure the marker's own content, not the full map viewport. Zero or unexpectedly
            // huge marker layouts indicate invalid content and are rejected before allocation.
            val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            container.measure(spec, spec)
            val width = container.measuredWidth
            val height = container.measuredHeight
            check(width > 0 && height > 0) { "MarkerComposable content must have a non-zero size" }
            require(width <= 2048 && height <= 2048) { "MarkerComposable bitmap exceeds 2048 pixels" }
            container.layout(0, 0, width, height)
            // Reuse the pixel buffer while dimensions stay equal. Clear first so transparent
            // regions do not retain pixels from the preceding icon/content state.
            val image = bitmap?.takeIf { it.width == width && it.height == height }
                ?: createBitmap(width, height).also { bitmap = it }
            image.eraseColor(android.graphics.Color.TRANSPARENT)
            composeView.draw(Canvas(image))
            // Publish a new descriptor even if the Bitmap instance was reused: consumers must
            // observe that its pixels changed and upload a refreshed native icon.
            onBitmap(BitmapDescriptor.Image(image))
        } finally { container.drawingBitmap = false }
    }
    /** Remove every external reference that could retain or redraw this marker's ComposeView. */
    fun dispose() {
        if (disposed) return
        disposed = true
        parent.removeCallbacks(render)
        composeView.disposeComposition()
        parent.removeView(container)
        // Drop our reference rather than recycle immediately; native overlay teardown can still
        // be releasing the last descriptor that referenced this buffer.
        bitmap = null
    }
}

/**
 * Capture descendant invalidations, including draw-only visual changes inside marker content.
 * Android 26+ uses onDescendantInvalidated; the legacy hook also covers supported older devices.
 * Both paths use requestRender's scheduling guard, so duplicate notifications are inexpensive.
 */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic host, never inflated from XML.
private class IconContainer(parent: ViewGroup) : FrameLayout(parent.context) {
    var onInvalidated: () -> Unit = {}
    var drawingBitmap = false
    @androidx.annotation.RequiresApi(26)
    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        if (!drawingBitmap) onInvalidated()
    }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun invalidateChildInParent(location: IntArray?, dirty: Rect?): ViewParent? {
        if (!drawingBitmap) onInvalidated()
        return super.invalidateChildInParent(location, dirty)
    }
}
