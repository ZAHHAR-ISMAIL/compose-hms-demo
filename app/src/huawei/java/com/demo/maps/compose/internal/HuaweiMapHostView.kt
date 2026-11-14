package com.demo.maps.compose.internal

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapView

/**
 * AndroidView's container for one HMS MapView. Compose does not automatically forward the SDK's
 * onCreate/onStart/onResume/onPause/onStop/onDestroy methods, so this host performs that work.
 *
 * The backend/applier own map content. This class owns only view lifecycle, layout readiness,
 * native Bundle restoration, and memory callbacks. release() is safe to call more than once.
 */
@android.annotation.SuppressLint("ViewConstructor") // Lifecycle dependencies are injected programmatically.
internal class HuaweiMapHostView(
    context: Context,
    private val lifecycle: Lifecycle,
    private val savedStateRegistry: SavedStateRegistry,
    private val stateKey: String,
    restoredState: Bundle?,
    onReady: (HuaweiMapHostView, HuaweiMap) -> Unit,
) : FrameLayout(context), LifecycleEventObserver, ComponentCallbacks2 {
    val mapView = MapView(context)
    // Installed by RenderHuaweiMap so the overlay composition/listeners are disposed while the
    // native map is still alive, before MapView.onDestroy makes its handles unusable.
    var onBeforeRelease: (() -> Unit)? = null
    // Track which SDK lifecycle transitions were actually sent; Compose removal and the owner's
    // destruction may both request cleanup, and the view can be created after ON_START/ON_RESUME.
    private var started = false
    private var resumed = false
    var released = false
        private set
    // Only one layout action is needed: attach this map's camera controller after dimensions exist.
    private var afterLayout: (() -> Unit)? = null
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (!released && mapView.width > 0 && mapView.height > 0) {
            afterLayout?.also { afterLayout = null; it() }
        }
    }

    init {
        // Restore SDK-owned state separately from the shared camera/marker state savers.
        mapView.onCreate(restoredState)
        addView(mapView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mapView.addOnLayoutChangeListener(layoutListener)
        // The surrounding saved-state owner asks this provider for a native Bundle during saving.
        savedStateRegistry.registerSavedStateProvider(stateKey) { Bundle().also(mapView::onSaveInstanceState) }
        lifecycle.addObserver(this)
        context.applicationContext.registerComponentCallbacks(this)
        // Catch up immediately if the view was inserted into an already resumed destination.
        syncLifecycle()
        // The callback may arrive after navigation removed the view; ignore that late result.
        mapView.getMapAsync { if (!released) onReady(this, it) }
    }

    /** Run camera attachment now if sized, or keep it for the first non-zero layout callback. */
    fun whenLaidOut(action: () -> Unit) {
        if (released) return
        if (mapView.width > 0 && mapView.height > 0) action() else afterLayout = action
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) release() else syncLifecycle()
    }
    /** Match native start/resume state to the current Compose lifecycle owner, in SDK order. */
    private fun syncLifecycle() {
        if (released) return
        val shouldStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val shouldResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (shouldStart && !started) { mapView.onStart(); started = true }
        if (shouldResume && !resumed) { mapView.onResume(); resumed = true }
        if (!shouldResume && resumed) { mapView.onPause(); resumed = false }
        if (!shouldStart && started) { mapView.onStop(); started = false }
    }

    /**
     * Release content first, stop native rendering second, then unregister every retained owner.
     * Called from both AndroidView.onRelease and ON_DESTROY, hence the early released guard.
     */
    fun release() {
        if (released) return
        onBeforeRelease?.invoke()
        onBeforeRelease = null
        released = true
        // A deferred camera attachment must never run against a destroyed native view.
        afterLayout = null
        mapView.removeOnLayoutChangeListener(layoutListener)
        if (resumed) mapView.onPause()
        if (started) mapView.onStop()
        resumed = false; started = false
        lifecycle.removeObserver(this)
        savedStateRegistry.unregisterSavedStateProvider(stateKey)
        context.applicationContext.unregisterComponentCallbacks(this)
        mapView.onDestroy()
    }
    // Registration used applicationContext, so explicit unregistration in release() is essential:
    // otherwise that long-lived context could retain the removed map through these callbacks.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() { if (!released) mapView.onLowMemory() }
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !released) mapView.onLowMemory()
    }
    // Layout and the saved-state/recreation path handle configuration changes; no extra SDK step.
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
