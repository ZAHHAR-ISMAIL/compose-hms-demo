package com.demo.maps.compose.internal

import com.demo.maps.model.LatLng
import earcut4j.Earcut
import kotlin.math.*

/** The bounded fallback applies only to a rectangular world envelope with non-geodesic holes. */
internal fun isWorldMask(options: PolygonOptions): Boolean {
    if (options.geodesic || options.holes.isEmpty()) return false
    val ring = normalizeRing(options.points)
    if (ring.size < 4) return false
    val west = ring.minOf { it.longitude }; val east = ring.maxOf { it.longitude }
    val south = ring.minOf { it.latitude }; val north = ring.maxOf { it.latitude }
    if (east-west < 359 || south > -80 || north < 80) return false
    return ring.all { it.longitude == west || it.longitude == east || it.latitude == south || it.latitude == north }
}

internal data class ProjectedPoint(val x: Double, val y: Double)
internal fun project(point: LatLng): ProjectedPoint {
    val latitude = point.latitude.coerceIn(-85.0511287798,85.0511287798)
    return ProjectedPoint((point.longitude+180)/360, (1-ln(tan(PI/4+Math.toRadians(latitude)/2))/PI)/2)
}
private fun unproject(point: ProjectedPoint) = LatLng(Math.toDegrees(atan(sinh(PI*(1-2*point.y)))),point.x*360-180)
internal fun projectedArea(points: List<ProjectedPoint>): Double = abs(points.indices.sumOf { index ->
    val next = points[(index+1)%points.size]
    points[index].x*next.y-next.x*points[index].y
}/2)

/**
 * Triangulate in Web Mercator (the native non-geodesic fill plane), then clip each convex triangle
 * into <=90-degree longitude bands. Interiors do not overlap, so translucent alpha is applied
 * once. No internal triangle/band edge is stroked. Original outlines are separate native lines.
 */
internal fun worldMaskPieces(points: List<LatLng>, holes: List<List<LatLng>>): List<List<LatLng>> {
    val rings = (listOf(points)+holes).map(::normalizeRing)
    require(rings.all { it.size >= 3 }) { "Mask rings need at least three distinct vertices" }
    val projected = rings.flatten().map(::project)
    val data = projected.flatMap { listOf(it.x,it.y) }.toDoubleArray()
    var offset = rings.first().size
    val starts = rings.drop(1).map { ring -> offset.also { offset += ring.size } }.toIntArray()
    val triangles = Earcut.earcut(data,starts,2)
    val pieces = mutableListOf<List<LatLng>>()
    for (triangle in triangles.chunked(3)) {
        val vertices = triangle.map { projected[it] }
        for (band in 0..3) {
            val clipped = clipX(clipX(vertices,band*.25,true),(band+1)*.25,false)
            if (clipped.size >= 3 && projectedArea(clipped) > 1e-18) pieces.add(clipped.map(::unproject))
        }
    }
    // Earcut may return a partial result for malformed/self-intersecting input. Never quietly
    // turn that into a partly missing mask. Synthetic fixtures do not validate production data.
    val expectedArea = projectedArea(rings.first().map(::project))-rings.drop(1).sumOf { projectedArea(it.map(::project)) }
    val actualArea = pieces.sumOf { projectedArea(it.map(::project)) }
    require(expectedArea > 0 && abs(actualArea-expectedArea) <= max(1e-10,expectedArea*1e-8)) { "Mask triangulation did not preserve outer-minus-hole area" }
    return pieces
}

private fun clipX(points: List<ProjectedPoint>, edge: Double, keepRight: Boolean): List<ProjectedPoint> {
    if (points.isEmpty()) return emptyList()
    val output = mutableListOf<ProjectedPoint>()
    fun inside(point: ProjectedPoint) = if (keepRight) point.x >= edge else point.x <= edge
    var previous = points.last()
    for (point in points) {
        if (inside(point) != inside(previous)) {
            val fraction = (edge-previous.x)/(point.x-previous.x)
            output.add(ProjectedPoint(edge,previous.y+(point.y-previous.y)*fraction))
        }
        if (inside(point)) output.add(point)
        previous = point
    }
    return output.distinct()
}

internal data class MaskFill(val points: List<LatLng>, val holes: List<List<LatLng>>)

/**
 * Cancel shared mesh edges within each band. Separate translucent native triangles can produce
 * antialias blending seams despite disjoint mathematical interiors. Reconstructing the boundary
 * leaves one native face (with holes where necessary) per connected band component. The bounded
 * <=90-degree faces render correctly in HMS; the original 360-degree polygon does not.
 */
internal fun mergeMaskPieces(pieces: List<List<LatLng>>): List<MaskFill> {
    data class Vertex(val x: Long, val y: Long) {
        fun point() = ProjectedPoint(x/1e12,y/1e12)
    }
    fun vertex(point: LatLng): Vertex = project(point).let { Vertex((it.x*1e12).roundToLong(),(it.y*1e12).roundToLong()) }
    fun signedArea(ring: List<Vertex>) = ring.indices.sumOf { index ->
        val a = ring[index].point(); val b = ring[(index+1)%ring.size].point()
        a.x*b.y-b.x*a.y
    }/2
    fun contains(ring: List<Vertex>, point: Vertex): Boolean {
        var inside = false
        var previous = ring.last().point()
        val p = point.point()
        for (v in ring) {
            val current = v.point()
            if ((current.y>p.y)!=(previous.y>p.y) && p.x<(previous.x-current.x)*(p.y-current.y)/(previous.y-current.y)+current.x) inside=!inside
            previous=current
        }
        return inside
    }
    val fills = mutableListOf<MaskFill>()
    val bands = pieces.groupBy { floor(it.map(::project).map { p -> p.x }.average()*4).toInt().coerceIn(0,3) }
    for (bandPieces in bands.values) {
        val edges = linkedSetOf<Pair<Vertex,Vertex>>()
        for (piece in bandPieces) {
            var ring = piece.map(::vertex).distinct()
            if (signedArea(ring)<0) ring=ring.asReversed()
            for (index in ring.indices) {
                val edge = ring[index] to ring[(index+1)%ring.size]
                if (!edges.remove(edge.second to edge.first)) check(edges.add(edge)) { "Duplicate mask mesh edge" }
            }
        }
        val remaining = edges.toMutableSet()
        val outgoing = edges.groupBy { it.first }
        val rings = mutableListOf<List<Vertex>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first().first
            var point = start
            val ring = mutableListOf<Vertex>()
            do {
                ring.add(point)
                val next = outgoing[point].orEmpty().filter { it in remaining }
                check(next.size == 1) { "Non-manifold mask boundary" }
                remaining.remove(next.single())
                point = next.single().second
            } while (point != start)
            if (abs(signedArea(ring))>1e-16) rings.add(ring)
        }
        val outer = rings.filter { signedArea(it)>0 }
        val holes = rings.filter { signedArea(it)<0 }
        holes.forEach { hole -> check(outer.count { contains(it,hole.first()) } == 1) { "Unowned mask hole" } }
        outer.forEach { ring -> fills.add(MaskFill(ring.map { unproject(it.point()) },
            holes.filter { contains(ring,it.first()) }.map { hole -> hole.map { unproject(it.point()) } })) }
    }
    val meshArea = pieces.sumOf { projectedArea(it.map(::project)) }
    val fillArea = fills.sumOf { projectedArea(it.points.map(::project))-it.holes.sumOf { hole -> projectedArea(hole.map(::project)) } }
    check(abs(meshArea-fillArea)<1e-9) { "Merged mask changed fill area" }
    return fills
}
