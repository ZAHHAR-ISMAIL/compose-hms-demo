package com.demo.maps.compose.internal

import com.demo.maps.clustering.Cluster
import com.demo.maps.clustering.ClusterItem
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import java.util.Collections
import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * HMS-only grouping, independent of Google's utility implementation. A world-anchored grid
 * stays fixed while panning. Its target width is 100 dp at integer zoom; nearby points across
 * cell boundaries can remain separate. Matching Google membership is deliberately unnecessary.
 * All items enter their cells BEFORE culling, so callbacks never receive a truncated group.
 */
internal suspend fun <T : ClusterItem> groupHmsItems(
    items: List<T>,
    zoom: Float,
    visibleBounds: LatLngBounds,
): List<Cluster<T>> {
    require(zoom.isFinite() && zoom in 0f..30f) { "Invalid clustering zoom: $zoom" }
    val context = currentCoroutineContext()
    context.ensureActive()
    val cells = ceil(256.0 * 2.0.pow(floor(zoom.toDouble())) / 100.0).toLong()
    val buckets = linkedMapOf<Long, MutableList<T>>()
    val seen = HashSet<T>()
    items.forEachIndexed { index, item ->
        if (index % 256 == 0) context.ensureActive()
        if (seen.add(item)) {
            val point = item.position
            val x = floor(projectX(point.longitude) * cells).toLong().coerceIn(0, cells - 1)
            val y = floor(projectY(point.latitude) * cells).toLong().coerceIn(0, cells - 1)
            buckets.getOrPut((x shl 32) or y) { mutableListOf() }.add(item)
        }
    }
    // One cell of overscan; longitude intervals may wrap across the date line. Test each cell
    // in adjacent world copies rather than maintaining a spatial tree or splitting item lists.
    val margin = 1.0 / cells
    val west = projectX(visibleBounds.southwest.longitude) - margin
    val east = projectX(visibleBounds.northeast.longitude) + margin +
        if (visibleBounds.southwest.longitude > visibleBounds.northeast.longitude) 1.0 else 0.0
    val north = projectY(visibleBounds.northeast.latitude) - margin
    val south = projectY(visibleBounds.southwest.latitude) + margin
    val result = mutableListOf<Cluster<T>>()
    buckets.entries.forEachIndexed { index, (key, members) ->
        if (index % 256 == 0) context.ensureActive()
        val x = (key ushr 32).toDouble() / cells
        val y = (key and 0xffffffffL).toDouble() / cells
        val visibleX = east - west >= 1.0 || (-1..1).any { shift ->
            x + shift <= east && x + margin + shift >= west
        }
        if (visibleX && y <= south && y + margin >= north) {
            val anchor = members.firstOrNull { visibleBounds.contains(it.position) } ?: members.first()
            result.add(GridCluster(anchor.position, members))
        }
    }
    return Collections.unmodifiableList(result)
}

private fun projectX(longitude: Double) = (longitude + 180.0) / 360.0
private fun projectY(latitude: Double): Double {
    val radians = Math.toRadians(latitude.coerceIn(-85.0511287798, 85.0511287798))
    return ((1.0 - ln(tan(PI / 4.0 + radians / 2.0)) / PI) / 2.0).coerceIn(0.0, 1.0)
}

/** Original application items, not SDK substitutes; callers can open lists or fit their bounds. */
private class GridCluster<T : ClusterItem>(private val center: LatLng, items: List<T>) : Cluster<T> {
    private val members = Collections.unmodifiableList(items.toList())
    override fun getPosition(): LatLng = center
    override fun getItems(): Collection<T> = members
    override fun getSize(): Int = members.size
}
