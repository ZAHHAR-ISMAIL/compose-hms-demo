package com.demo.maps

import com.demo.maps.clustering.ClusterItem
import com.demo.maps.compose.internal.groupHmsItems
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class HuaweiGridClusteringTest {
    private data class Stop(val id: Int, val coordinate: LatLng) : ClusterItem {
        override fun getPosition() = coordinate
        override fun getTitle() = "Stop $id"
        override fun getSnippet() = "Route ${id % 3}"
        override fun getZIndex(): Float? = id.toFloat()
    }
    private val world = LatLngBounds(LatLng(-90.0, -180.0), LatLng(90.0, 179.999999))
    private fun bounds(west: Double, east: Double) = LatLngBounds(LatLng(0.0, west), LatLng(.002, east))

    @Test fun emptySingletonAndTwoItemGroupsPreserveItemsAndMetadata() = runTest {
        assertTrue(groupHmsItems<Stop>(emptyList(), 13f, world).isEmpty())
        val a = Stop(1, LatLng(.001, .001))
        val b = Stop(2, a.coordinate)
        val single = groupHmsItems(listOf(a), 13f, world).single()
        assertEquals(1, single.size)
        assertSame(a, single.items.single())
        val cluster = groupHmsItems(listOf(a, a.copy(), b), 13f, world).single()
        assertEquals(2, cluster.size) // Equal items do not inflate the application threshold.
        assertEquals(listOf(a, b), cluster.items.toList())
        assertSame(b, cluster.items.last())
        assertEquals("Stop 2", cluster.items.last().title)
        assertEquals("Route 2", cluster.items.last().snippet)
        assertEquals(2f, cluster.items.last().zIndex)
        assertTrue(runCatching { (cluster.items as MutableCollection<*>).clear() }.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test fun cullingKeepsTheFullCellAndChoosesAVisibleAnchor() = runTest {
        val a = Stop(1, LatLng(.001, .0001))
        val b = Stop(2, LatLng(.001, .001))
        val c = Stop(3, LatLng(.001, .002))
        val far = Stop(4, LatLng(.001, .04))
        val items = listOf(a, b, c, far)
        val first = groupHmsItems(items, 13f, bounds(.0009, .0011)).single()
        assertEquals(listOf(a, b, c), first.items.toList())
        assertEquals(b.coordinate, first.position)
        val panned = groupHmsItems(items, 13f, bounds(.0019, .0021)).single()
        assertEquals(first.items.toSet(), panned.items.toSet())
        assertEquals(c.coordinate, panned.position)
        val resized = groupHmsItems(items, 13f, bounds(-.01, .05))
        assertEquals(setOf(a, b, c, far), resized.flatMap { it.items }.toSet())
        assertEquals(first.items.toSet(), resized.single { it.size == 3 }.items.toSet())
        val overscanOnly = groupHmsItems(items, 13f, bounds(-.002, -.001)).single()
        assertEquals(listOf(a, b, c), overscanOnly.items.toList())
        assertEquals("An overscan-only cell falls back to its first member", a.coordinate, overscanOnly.position)
    }

    @Test fun cellEdgesAndIntegerZoomHaveExplicitIndependentRules() = runTest {
        // Longitude zero is a cell edge at zoom 13. Proximity alone does not merge neighbors.
        val edge = listOf(Stop(1, LatLng(0.0, -.000001)), Stop(2, LatLng(0.0, .000001)))
        assertEquals(listOf(1, 1), groupHmsItems(edge, 13f, world).map { it.size })
        val items = listOf(Stop(1, LatLng(.001, .003)), Stop(2, LatLng(.001, .012)))
        assertEquals(2, groupHmsItems(items, 13f, world).single().size)
        assertEquals(2, groupHmsItems(items, 13.99f, world).single().size)
        assertEquals(2, groupHmsItems(items, 14f, world).size)
        val changed = groupHmsItems(items.drop(1) + Stop(3, items.last().coordinate), 14f, world).single()
        assertEquals(setOf(2, 3), changed.items.map { it.id }.toSet())
    }

    @Test fun wrappedViewportsPolesAndLargeSnapshotsKeepEveryEligibleItem() = runTest {
        val items = listOf(Stop(1, LatLng(0.0, 179.999)), Stop(2, LatLng(0.0, -179.999)), Stop(3, LatLng(0.0, 0.0)))
        val dateline = LatLngBounds(LatLng(-1.0, 179.99), LatLng(1.0, -179.99))
        assertEquals(setOf(1, 2), groupHmsItems(items, 13f, dateline).flatMap { it.items }.map { it.id }.toSet())
        val poles = listOf(Stop(1, LatLng(90.0, 0.0)), Stop(2, LatLng(-90.0, 0.0)))
        assertEquals(poles.toSet(), groupHmsItems(poles, 13f, world).flatMap { it.items }.toSet())
        val many = List(5000) { Stop(it, LatLng(24.7 + it * .000001, 46.67)) }
        val groups = groupHmsItems(many, 15f, world)
        assertEquals(many.toSet(), groups.flatMap { it.items }.toSet())
        assertEquals(many.size, groups.sumOf { it.size })
    }

    @Test fun cancelledGenerationStopsScanningBeforeItCanPublish() = runTest {
        val generation = Job()
        var reads = 0
        val items = List(2000) { id -> object : ClusterItem {
            override fun getPosition(): LatLng {
                if (++reads == 300) generation.cancel()
                return LatLng(.001, id * .000001)
            }
            override fun getTitle(): String? = null
            override fun getSnippet(): String? = null
            override fun getZIndex(): Float? = null
        } }
        val result = runCatching { withContext(generation) { groupHmsItems(items, 13f, world) } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue("Large requests must cooperate with disposal", reads <= 512)
    }
}
