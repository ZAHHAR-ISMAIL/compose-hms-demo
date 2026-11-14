package com.demo.maps.model

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/** Load the same resource name from each flavor; Google and HMS JSON formats differ. */
@Immutable
data class MapStyleOptions(val json: String) {
    companion object {
        @JvmStatic fun loadRawResourceStyle(context: Context, resourceId: Int) = MapStyleOptions(
            context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() },
        )
    }
}

sealed interface BitmapDescriptor {
    data class Resource(val resourceId: Int) : BitmapDescriptor
    class Image(val bitmap: Bitmap) : BitmapDescriptor
    data class Default(val hue: Float) : BitmapDescriptor
}

object BitmapDescriptorFactory {
    const val HUE_RED = 0f
    const val HUE_BLUE = 240f
    const val HUE_GREEN = 120f
    fun fromResource(resourceId: Int): BitmapDescriptor = BitmapDescriptor.Resource(resourceId)
    fun fromBitmap(bitmap: Bitmap): BitmapDescriptor = BitmapDescriptor.Image(bitmap)
    fun defaultMarker(hue: Float = HUE_RED): BitmapDescriptor = BitmapDescriptor.Default(hue)
}

/** Callback handle. Native SDK objects never cross the public API boundary. */
interface Marker {
    val id: String
    var position: LatLng
    var rotation: Float
    var tag: Any?
    fun showInfoWindow()
    fun hideInfoWindow()
}

interface Polygon { val id: String; var tag: Any? }
interface Polyline { val id: String; var tag: Any? }
interface Circle { val id: String; var tag: Any? }
