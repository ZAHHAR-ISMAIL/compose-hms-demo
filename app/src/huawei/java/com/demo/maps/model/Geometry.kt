package com.demo.maps.model

import androidx.compose.runtime.Immutable

/**
 * Huawei's compatibility coordinate. Retained unchanged when moving out of main so HMS keeps its
 * finite-value checks, normalization, equality and bounds behavior. Google uses native aliases.
 * These model names are shared by source, not by runtime class or cross-flavor serialization.
 */
@Immutable
class LatLng(latitude: Double, longitude: Double) {
    val latitude: Double = latitude.coerceIn(-90.0, 90.0)
    val longitude: Double = wrapLongitude(longitude)

    init { require(latitude.isFinite() && longitude.isFinite()) { "Coordinates must be finite" } }

    override fun equals(other: Any?) = other is LatLng &&
        latitude.toBits() == other.latitude.toBits() && longitude.toBits() == other.longitude.toBits()
    override fun hashCode() = 31 * latitude.hashCode() + longitude.hashCode()
    override fun toString() = "lat/lng: ($latitude,$longitude)"
}

@Immutable
data class LatLngBounds(val southwest: LatLng, val northeast: LatLng) {
    init { require(southwest.latitude <= northeast.latitude) { "Southern latitude exceeds northern latitude" } }

    val center: LatLng get() = LatLng(
        (southwest.latitude + northeast.latitude) / 2,
        (southwest.longitude + northeast.longitude + if (crossesAntimeridian) 360 else 0) / 2,
    )
    private val crossesAntimeridian get() = southwest.longitude > northeast.longitude

    fun contains(point: LatLng): Boolean = point.latitude in southwest.latitude..northeast.latitude &&
        if (crossesAntimeridian) point.longitude >= southwest.longitude || point.longitude <= northeast.longitude
        else point.longitude in southwest.longitude..northeast.longitude

    fun including(point: LatLng): LatLngBounds {
        var west = southwest.longitude
        var east = northeast.longitude
        val inside = if (crossesAntimeridian) point.longitude >= west || point.longitude <= east
            else point.longitude in west..east
        if (!inside) {
            if ((west - point.longitude + 360) % 360 < (point.longitude - east + 360) % 360) west = point.longitude
            else east = point.longitude
        }
        return LatLngBounds(LatLng(minOf(southwest.latitude, point.latitude), west),
            LatLng(maxOf(northeast.latitude, point.latitude), east))
    }

    class Builder {
        private var south = Double.POSITIVE_INFINITY
        private var north = Double.NEGATIVE_INFINITY
        private var west: Double? = null
        private var east = 0.0

        fun include(point: LatLng): Builder = apply {
            south = minOf(south, point.latitude)
            north = maxOf(north, point.latitude)
            val currentWest = west
            if (currentWest == null) {
                west = point.longitude
                east = point.longitude
            } else {
                val inside = if (currentWest <= east) point.longitude in currentWest..east
                else point.longitude >= currentWest || point.longitude <= east
                if (!inside) {
                    val extendWest = (currentWest - point.longitude + 360) % 360
                    val extendEast = (point.longitude - east + 360) % 360
                    if (extendWest < extendEast) west = point.longitude else east = point.longitude
                }
            }
        }

        fun build(): LatLngBounds {
            val western = checkNotNull(west) { "Cannot build bounds without coordinates" }
            return LatLngBounds(LatLng(south, western), LatLng(north, east))
        }
    }

    companion object { @JvmStatic fun builder() = Builder() }
}

@Immutable
data class CameraPosition(val target: LatLng, val zoom: Float, val tilt: Float = 0f, val bearing: Float = 0f) {
    init {
        require(zoom.isFinite() && tilt.isFinite() && bearing.isFinite())
        require(tilt in 0f..90f)
    }
    companion object {
        @JvmStatic fun fromLatLngZoom(target: LatLng, zoom: Float) = CameraPosition(target, zoom)
    }
}

internal fun wrapLongitude(value: Double): Double = if (value >= -180 && value < 180) value
else ((value + 180) % 360 + 360) % 360 - 180
