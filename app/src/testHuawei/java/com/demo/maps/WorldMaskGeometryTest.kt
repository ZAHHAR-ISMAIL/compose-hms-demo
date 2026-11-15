package com.demo.maps

import com.demo.maps.compose.internal.*
import com.demo.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class WorldMaskGeometryTest {
    private val world = listOf(LatLng(-85.0511,-180.0),LatLng(-85.0511,0.0),LatLng(-85.0511,179.9999),LatLng(85.0511,179.9999),LatLng(85.0511,0.0),LatLng(85.0511,-180.0))
    private val hole = listOf(LatLng(24.68,46.65),LatLng(24.68,46.69),LatLng(24.72,46.69),LatLng(24.72,46.65))
    @Test fun piecesPreserveAreaDoNotCoverHoleAndStayWithinNinetyDegreeBands() {
        val pieces = worldMaskPieces(world,listOf(hole))
        assertTrue(pieces.isNotEmpty())
        assertEquals(projectedArea(world.map(::project))-projectedArea(hole.map(::project)),pieces.sumOf { projectedArea(it.map(::project)) },1e-10)
        val merged = mergeMaskPieces(pieces)
        assertTrue(merged.size <= 4)
        assertEquals(1,merged.sumOf { it.holes.size })
        pieces.forEach { assertTrue(it.maxOf { p -> p.longitude }-it.minOf { p -> p.longitude } <= 90.00000001) }
        // An interior probe must be in zero fill pieces; an exterior probe in exactly one.
        fun count(point: LatLng) = pieces.count { contains(it.map(::project),project(point)) }
        assertEquals(0,count(LatLng(24.7,46.67)))
        assertEquals(1,count(LatLng(24.75,46.6)))
    }
    @Test fun windingClosureAndConcaveHolesAreHandledAndOrdinaryPolygonsStayNative() {
        val concave = hole.take(2)+listOf(LatLng(24.72,46.69),LatLng(24.72,46.675),LatLng(24.695,46.675),LatLng(24.695,46.665),LatLng(24.72,46.665),LatLng(24.72,46.65))
        val pieces = worldMaskPieces(world.asReversed()+world.last(),listOf(concave.asReversed()+concave.last()))
        assertEquals(projectedArea(world.map(::project))-projectedArea(concave.map(::project)),pieces.sumOf { projectedArea(it.map(::project)) },1e-10)
        assertTrue(isWorldMask(PolygonOptions(world,holes=listOf(hole))))
        assertFalse(isWorldMask(PolygonOptions(world,holes=listOf(hole),geodesic=true)))
        assertFalse(isWorldMask(PolygonOptions(hole,holes=emptyList())))
        assertTrue(runCatching { worldMaskPieces(world,listOf(emptyList())) }.isFailure)
    }
    private fun contains(ring: List<ProjectedPoint>,point: ProjectedPoint): Boolean {
        var inside = false
        var previous = ring.last()
        for (vertex in ring) {
            if ((vertex.y>point.y)!=(previous.y>point.y) && point.x<(previous.x-vertex.x)*(point.y-vertex.y)/(previous.y-vertex.y)+vertex.x) inside=!inside
            previous=vertex
        }
        return inside
    }
}
