package com.demo.maps.compose.internal

import androidx.compose.ui.unit.IntSize
import com.demo.hmscomposeapp.map.bitmapDescriptorFromVector
import com.demo.maps.clustering.Cluster
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.clustering.internal.algorithm.NonHierarchicalViewBasedAlgorithm
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.Marker as MarkerHandle
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import kotlinx.coroutines.*

/** Owns ONLY this layer's markers and job; ordinary vehicle markers belong to other nodes. */
internal class HuaweiClusterNode<T : ClusterItem>(private val scene: HuaweiMapScene, private val scope: CoroutineScope) : MarkerInputNode {
    var callbacks: Triple<(Cluster<T>) -> Boolean, (T) -> Boolean, (MarkerHandle) -> Boolean> = Triple({ false }, { false }, { false })
    private data class Entry<T : ClusterItem>(val marker: Marker, var cluster: Cluster<T>?, var item: T?)
    private val entries = linkedMapOf<Any, Entry<T>>()
    private var job: Job? = null
    private var disposed = false
    private var iconResource: Int? = null
    private var icon: com.huawei.hms.maps.model.BitmapDescriptor? = null

    fun submit(items: List<T>, camera: CameraPosition, viewport: IntSize, resource: Int) {
        job?.cancel()
        job = scope.launch(Dispatchers.Main.immediate) {
            // Each generation owns its algorithm; disposal/cancellation cannot race mutations.
            val clusters = withContext(Dispatchers.Default) {
                val algorithm = NonHierarchicalViewBasedAlgorithm<T>(viewport.width, viewport.height)
                algorithm.addItems(items)
                algorithm.onCameraChange(camera)
                algorithm.getClusters(camera.zoom)
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
                    .position(position.native()).icon(icon).anchor(.5f, if (cluster != null) 1f else .5f))), cluster, item)
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
                // maps-utils 3.8.2 uses >= minClusterSize, and the supplied renderer sets it to 2.
                if (cluster.size >= 2) put(ClusterKey(cluster.items.toSet()), cluster, null)
                else cluster.items.forEach { put(it, null, it) }
            }
            entries.keys.filter { it !in retained }.forEach { entries.remove(it)?.marker?.remove() }
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
