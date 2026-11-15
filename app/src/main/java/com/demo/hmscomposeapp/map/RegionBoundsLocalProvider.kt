package com.demo.hmscomposeapp.map

import android.content.Context
import com.demo.hmscomposeapp.R
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import org.json.JSONObject
import java.util.WeakHashMap

data class MapBoundaries(val world: List<LatLng>, val riyadh: List<LatLng>)
interface RegionBoundsProvider {
    fun getCoordinatesPair(): MapBoundaries
    fun getRiyadhLatLngBounds(): LatLngBounds
}

/**
 * Keeps the supplied synchronous Context constructor/getters. Production DI can still inject its
 * application context. The resource here resolves to a clearly labelled synthetic demo asset.
 * Instances share a cache per application context AND resolved filename; failed loads are not cached.
 */
class RegionBoundsLocalProvider(context: Context) : RegionBoundsProvider {
    private val app = context.applicationContext
    private val assetName = context.getString(R.string.region_boundary_asset)
    private val boundaries: MapBoundaries by lazy {
        synchronized(cache) {
            cache.getOrPut(app) { mutableMapOf() }.getOrPut(assetName) {
                app.assets.open(assetName).bufferedReader().use { parseBoundaries(it.readText()) }
            }
        }
    }
    override fun getCoordinatesPair() = boundaries
    override fun getRiyadhLatLngBounds(): LatLngBounds {
        require(boundaries.riyadh.isNotEmpty()) { "Region ring is empty" }
        return LatLngBounds.builder().apply { boundaries.riyadh.forEach(::include) }.build()
    }
    companion object {
        private val cache = WeakHashMap<Context, MutableMap<String, MapBoundaries>>()
        internal fun clearCacheForTests() = synchronized(cache) { cache.clear() }
    }
}

/** Validate before calling SDK constructors (which otherwise silently clamp/wrap invalid data). */
internal fun parseBoundaries(json: String): MapBoundaries {
    val root = JSONObject(json)
    require(root.optString("type") == "FeatureCollection") { "Expected a GeoJSON FeatureCollection" }
    val features = root.getJSONArray("features")
    require(features.length() > 0) { "Boundary features are empty" }
    val geometry = features.getJSONObject(0).getJSONObject("geometry")
    require(geometry.getString("type") == "Polygon") { "Expected Polygon geometry" }
    val rings = geometry.getJSONArray("coordinates")
    require(rings.length() >= 2) { "World and region rings are required" }
    fun ring(index: Int): List<LatLng> {
        val coordinates = rings.getJSONArray(index)
        require(coordinates.length() >= 3) { "Boundary ring $index is empty or too short" }
        val result = List(coordinates.length()) { pointIndex ->
            val point = coordinates.getJSONArray(pointIndex)
            require(point.length() >= 2) { "Expected longitude/latitude pair" }
            val longitude = point.getDouble(0)
            val latitude = point.getDouble(1)
            require(longitude.isFinite() && latitude.isFinite() && longitude in -180.0..180.0 && latitude in -90.0..90.0) { "Invalid boundary coordinate" }
            LatLng(latitude, longitude)
        }
        require(result.distinct().size >= 3) { "Boundary ring $index has fewer than three distinct vertices" }
        return java.util.Collections.unmodifiableList(result)
    }
    return MapBoundaries(world = ring(0), riyadh = ring(1))
}
