package com.demo.maps.compose.internal

import androidx.compose.ui.graphics.toArgb
import com.demo.maps.model.LatLng

/** Strip duplicate closure/consecutive vertices; HMS receives holes at creation, not one frame later. */
internal fun normalizeRing(points: List<LatLng>): List<LatLng> {
    val clean = points.fold(mutableListOf<LatLng>()) { out, point -> out.apply { if (lastOrNull() != point) add(point) } }
    if (clean.size > 1 && clean.first() == clean.last()) clean.removeAt(clean.lastIndex)
    return clean
}
private fun area(points: List<LatLng>) = points.indices.sumOf { index ->
    val next = points[(index+1)%points.size]
    points[index].longitude*next.latitude-next.longitude*points[index].latitude
}
internal fun normalizedHoles(options: PolygonOptions): List<List<LatLng>> {
    val outerDirection = area(normalizeRing(options.points))
    return options.holes.map(::normalizeRing).map { if (area(it)*outerDirection > 0) it.asReversed() else it }
}
internal fun PolygonOptions.nativeOptions() = com.huawei.hms.maps.model.PolygonOptions()
    .addAll(normalizeRing(points).map { it.native() }).apply {
        normalizedHoles(this@nativeOptions).forEach { addHole(it.map { point -> point.native() }) }
        clickable(clickable); fillColor(this@nativeOptions.fillColor.toArgb()); geodesic(geodesic)
        strokeColor(this@nativeOptions.strokeColor.toArgb()); strokeWidth(strokeWidth); visible(visible); zIndex(zIndex)
    }
