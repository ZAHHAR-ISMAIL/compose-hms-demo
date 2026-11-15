package com.demo.maps.compose

import androidx.annotation.DrawableRes
import androidx.compose.runtime.*
import com.demo.maps.clustering.Cluster
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.compose.internal.HuaweiClusterNode
import com.demo.maps.compose.internal.HuaweiMapApplier
import com.demo.maps.compose.internal.scene
import com.demo.maps.model.Marker

/**
 * Same helper/callback contract as Google, with independent HMS grid grouping. The caller keeps
 * its screen code; complete member lists survive viewport culling even when groups differ.
 * Native marker ownership and listener dispatch stay in this flavor's retained node.
 */
@Composable
@GoogleMapComposable
fun <T : ClusterItem> AppClusteringWrapper(
    items: Collection<T>,
    onClusterClick: (Cluster<T>) -> Boolean = { false },
    onClusterItemClick: (T) -> Boolean = { false },
    onExternalItemClick: (Marker) -> Boolean = { false },
    @DrawableRes markerImageResId: Int,
) {
    val scene = scene()
    val scope = rememberCoroutineScope()
    val viewport = LocalMapViewport.current
    val camera = currentCameraPositionState
    val snapshot = items.toList()
    ComposeNode<HuaweiClusterNode<T>, HuaweiMapApplier>(
        factory = { HuaweiClusterNode(scene, scope) },
        update = {
            set(Triple(onClusterClick, onClusterItemClick, onExternalItemClick)) { callbacks = it }
            // Do not regroup every frame during a gesture; fixed-zoom idle panning DOES regroup.
            set(listOf(snapshot, viewport, if (!camera.isMoving) camera.position else null, markerImageResId)) {
                if (!camera.isMoving && viewport.width > 0 && viewport.height > 0)
                    submit(snapshot, camera.position, markerImageResId)
            }
        },
    )
}
