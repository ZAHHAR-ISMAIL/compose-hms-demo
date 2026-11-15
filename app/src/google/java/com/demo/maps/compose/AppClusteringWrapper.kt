package com.demo.maps.compose

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.demo.hmscomposeapp.map.bitmapDescriptorFromVector
import com.demo.maps.clustering.Cluster
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.model.Marker
import com.google.android.gms.maps.GoogleMap as NativeMap
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.collections.SceneOwnedMarkerManager
import com.google.maps.android.compose.InputHandler
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlinx.coroutines.*

/** Native utility types stay native; the app-facing helper contract matches the Huawei flavor. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
fun <T : ClusterItem> AppClusteringWrapper(
    items: Collection<T>,
    onClusterClick: (Cluster<T>) -> Boolean = { false },
    onClusterItemClick: (T) -> Boolean = { false },
    onExternalItemClick: (Marker) -> Boolean = { false },
    @DrawableRes markerImageResId: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manager by remember { mutableStateOf<OwnedClusterManager<T>?>(null) }
    val viewport = LocalMapViewport.current
    val camera = currentCameraPositionState
    val snapshot = items.toList()
    MapEffect(context, markerImageResId) { map -> withContext(Dispatchers.Main.immediate) {
        val native = OwnedClusterManager<T>(context, map, scope)
        native.renderer = RetainedImageRenderer(context, map, native, markerImageResId)
        native.setAnimation(false)
        manager = native
        try { awaitCancellation() } finally { native.dispose(); if (manager === native) manager = null }
    } }
    val native = manager ?: return
    // Maps Compose dispatches these in declaration order, alongside ordinary vehicle markers.
    InputHandler(
        onMarkerClick = { native.markerManager.onMarkerClick(it) || onExternalItemClick(it) },
        onInfoWindowClick = native.markerManager::onInfoWindowClick,
        onInfoWindowLongClick = native.markerManager::onInfoWindowLongClick,
        onMarkerDragStart = native.markerManager::onMarkerDragStart,
        onMarkerDrag = native.markerManager::onMarkerDrag,
        onMarkerDragEnd = native.markerManager::onMarkerDragEnd,
    )
    SideEffect {
        native.setOnClusterClickListener(onClusterClick)
        native.setOnClusterItemClickListener(onClusterItemClick)
    }
    LaunchedEffect(native, snapshot, viewport, camera.isMoving, camera.position) { withContext(Dispatchers.Main.immediate) {
        if (!camera.isMoving && viewport.width > 0 && viewport.height > 0) {
            // The supplied helper used screen pixels. The algorithm explicitly expects map dp.
            val algorithm = NonHierarchicalViewBasedAlgorithm<T>(viewport.width, viewport.height)
            algorithm.addItems(snapshot)
            algorithm.onCameraChange(camera.position)
            // setAlgorithm copies old items; clear first so removed stations cannot reappear.
            native.clearItems()
            native.algorithm = algorithm
        }
    } }
}

/** Replace the native manager's unowned AsyncTask with a cancellable composition-owned job. */
private class OwnedClusterManager<T : ClusterItem>(context: Context, private val map: NativeMap,
    private val scope: CoroutineScope) : ClusterManager<T>(context, map, SceneOwnedMarkerManager(map)) {
    private var job: Job? = null
    private var disposed = false
    override fun cluster() {
        if (disposed) return
        job?.cancel()
        val algorithm = algorithm
        val zoom = map.cameraPosition.zoom
        job = scope.launch(Dispatchers.Main.immediate) {
            val groups = withContext(Dispatchers.Default) {
                algorithm.lock()
                try { algorithm.getClusters(zoom) } finally { algorithm.unlock() }
            }
            ensureActive()
            if (!disposed) renderer.onClustersChanged(groups)
        }
    }
    fun dispose() {
        disposed = true; job?.cancel(); job = null
        renderer.onRemove()
        markerCollection.clear(); clusterMarkerCollection.clear()
        clearItems()
        setOnClusterClickListener(null); setOnClusterItemClickListener(null)
    }
}

/**
 * The supplied custom DefaultClusterRenderer, with its same image, threshold and anchors.
 * Its disabled-animation render path is synchronous and retained: the upstream renderer's
 * private Handler/executor cannot be cancelled on disposal. No queued renderer can resurrect
 * markers after navigation, and neither collection ever includes ordinary vehicle markers.
 */
private class RetainedImageRenderer<T : ClusterItem>(context: Context, map: NativeMap,
    private val manager: ClusterManager<T>, @DrawableRes resource: Int) : DefaultClusterRenderer<T>(context, map, manager) {
    private val icon = bitmapDescriptorFromVector(context, resource)
    private data class Entry<T : ClusterItem>(val marker: Marker, var cluster: Cluster<T>?, var item: T?)
    private data class GroupKey(val items: Set<*>)
    private val entries = linkedMapOf<Any, Entry<T>>()
    private var clusterClick: ClusterManager.OnClusterClickListener<T>? = null
    private var itemClick: ClusterManager.OnClusterItemClickListener<T>? = null
    private var disposed = false
    init { minClusterSize = 2 }
    override fun setOnClusterClickListener(listener: ClusterManager.OnClusterClickListener<T>?) { clusterClick = listener }
    override fun setOnClusterItemClickListener(listener: ClusterManager.OnClusterItemClickListener<T>?) { itemClick = listener }
    override fun onAdd() {
        manager.markerCollection.setOnMarkerClickListener { marker -> entries.values.firstOrNull { it.marker == marker }?.item?.let { itemClick?.onClusterItemClick(it) } ?: false }
        manager.clusterMarkerCollection.setOnMarkerClickListener { marker -> entries.values.firstOrNull { it.marker == marker }?.cluster?.let { clusterClick?.onClusterClick(it) } ?: false }
    }
    override fun onClustersChanged(clusters: Set<Cluster<T>>) {
        if (disposed) return
        val retained = mutableSetOf<Any>()
        fun put(key: Any, cluster: Cluster<T>?, item: T?) {
            retained.add(key)
            val position = cluster?.position ?: checkNotNull(item).position
            val collection = if (cluster != null) manager.clusterMarkerCollection else manager.markerCollection
            val entry = entries[key] ?: Entry(collection.addMarker(MarkerOptions().position(position).icon(icon)
                .anchor(.5f, if (cluster != null) 1f else .5f)), cluster, item).also { entries[key] = it; com.demo.hmscomposeapp.map.MapPerformanceProbe.marker(true) }
            entry.cluster = cluster; entry.item = item
            val title = item?.title ?: item?.snippet
            val snippet = if (item?.title != null) item.snippet else null
            val changed = entry.marker.position != position || entry.marker.title != title || entry.marker.snippet != snippet
            entry.marker.position = position
            entry.marker.title = title; entry.marker.snippet = snippet
            entry.marker.zIndex = item?.zIndex ?: 0f
            // Match DefaultClusterRenderer's refresh of an already-open native info window.
            if (changed && entry.marker.isInfoWindowShown) entry.marker.showInfoWindow()
        }
        for (cluster in clusters) {
            if (shouldRenderAsCluster(cluster)) put(GroupKey(cluster.items.toSet()), cluster, null)
            else cluster.items.forEach { put(it, null, it) }
        }
        entries.keys.filter { it !in retained }.forEach { key ->
            entries.remove(key)?.let { (if (it.cluster != null) manager.clusterMarkerCollection else manager.markerCollection).remove(it.marker) }
        }
    }
    override fun onRemove() {
        disposed = true
        super.onRemove()
        entries.clear(); clusterClick = null; itemClick = null
    }
}
