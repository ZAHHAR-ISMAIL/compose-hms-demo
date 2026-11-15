package com.demo.maps.compose.internal

import com.demo.hmscomposeapp.map.bitmapDescriptorFromVector
import com.demo.maps.clustering.Cluster
import com.demo.maps.clustering.ClusterItem
import android.os.Looper
import android.view.View
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.Marker as MarkerHandle
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Owns ONLY this layer's markers and job; ordinary vehicle markers belong to other nodes. */
internal class HuaweiClusterNode<T : ClusterItem>(private val scene: HuaweiMapScene, private val scope: CoroutineScope) : MarkerInputNode {
    var callbacks: Triple<(Cluster<T>) -> Boolean, (T) -> Boolean, (MarkerHandle) -> Boolean> = Triple({ false }, { false }, { false })
    private data class Entry<T : ClusterItem>(val marker: Marker, var cluster: Cluster<T>?, var item: T?)
    private val entries = linkedMapOf<Any, Entry<T>>()
    private var job: Job? = null
    private var disposed = false
    private var iconResource: Int? = null
    private var icon: com.huawei.hms.maps.model.BitmapDescriptor? = null

    fun submit(items: List<T>, camera: CameraPosition, resource: Int) {
        job?.cancel()
        job = scope.launch(Dispatchers.Main.immediate) {
            // The camera host's layout callback belongs to camera attachment. Use our own
            // cancellable listener rather than replacing that callback with a cluster request.
            awaitLayout()
            ensureActive()
            val nativeBounds = scene.map.projection.visibleRegion.latLngBounds
            val bounds = LatLngBounds(nativeBounds.southwest.shared(), nativeBounds.northeast.shared())
            val clusters = withContext(Dispatchers.Default) {
                groupHmsItems(items, camera.zoom, bounds)
            }
            ensureActive()
            if (disposed) return@launch
            val changedIcon = iconResource != resource
            if (changedIcon) { icon = bitmapDescriptorFromVector(scene.host.context, resource).native(); iconResource = resource }
            val retained = mutableSetOf<Any>()
            fun put(key: Any, cluster: Cluster<T>?, item: T?) {
                retained.add(key)
                val position = cluster?.position ?: checkNotNull(item).position
                val existing = entries[key]
                val entry = existing ?: Entry(checkNotNull(scene.map.addMarker(MarkerOptions()
                    .position(position.native()).icon(icon).clusterable(false)
                    .anchor(.5f, if (cluster != null) 1f else .5f))), cluster, item)
                    .also { entries[key] = it; com.demo.hmscomposeapp.map.MapPerformanceProbe.marker(true) }
                entry.cluster = cluster; entry.item = item
                val nativePosition = position.native()
                val title = item?.title ?: item?.snippet
                val snippet = if (item?.title != null) item.snippet else null
                val changed = entry.marker.position != nativePosition || entry.marker.title != title || entry.marker.snippet != snippet
                entry.marker.position = nativePosition
                entry.marker.title = title
                entry.marker.snippet = snippet
                entry.marker.zIndex = item?.zIndex ?: 0f
                if (changed && entry.marker.isInfoWindowShown) entry.marker.showInfoWindow()
                if (changedIcon && existing != null) { entry.marker.setIcon(icon); com.demo.hmscomposeapp.map.MapPerformanceProbe.icon() }
            }
            for (cluster in clusters) {
                // Keep the application threshold even though HMS membership differs from Google.
                if (cluster.size >= 2) put(ClusterKey(cluster.items.toSet()), cluster, null)
                else cluster.items.forEach { put(it, null, it) }
            }
            entries.keys.filter { it !in retained }.forEach { entries.remove(it)?.marker?.remove() }
        }
    }
    private suspend fun awaitLayout() = suspendCancellableCoroutine<Unit> { continuation ->
        val view = scene.host.mapView
        if (view.width > 0 && view.height > 0 && !view.isLayoutRequested) {
            continuation.resume(Unit)
        } else {
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (v.width > 0 && v.height > 0) {
                        v.removeOnLayoutChangeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            view.addOnLayoutChangeListener(listener)
            continuation.invokeOnCancellation {
                if (Looper.myLooper() == Looper.getMainLooper()) view.removeOnLayoutChangeListener(listener)
                else view.post { view.removeOnLayoutChangeListener(listener) }
            }
        }
    }
    private data class ClusterKey(val items: Set<*>)
    override fun onMarkerClick(marker: Marker): Boolean {
        val entry = entries.values.firstOrNull { it.marker.id == marker.id }
        val consumed = entry?.cluster?.let(callbacks.first) ?: entry?.item?.let(callbacks.second) ?: false
        return consumed || callbacks.third(HuaweiMarker(marker))
    }
    override fun remove() {
        if (disposed) return
        disposed = true; job?.cancel(); job = null
        entries.values.forEach { it.marker.remove() }; entries.clear()
        callbacks = Triple({ false }, { false }, { false })
    }
}
