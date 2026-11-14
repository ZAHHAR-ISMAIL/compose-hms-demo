package com.demo.maps

import android.graphics.Point
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds

/**
 * HMS-only semantic commands, translated by HuaweiCameraController when the native map is ready.
 * Keeping these deferred values preserves the existing HMS startup behavior. The google flavor
 * aliases the native Google command/factory instead and has its original SDK setup requirements.
 */
sealed interface CameraUpdate {
    data class Position(val position: CameraPosition) : CameraUpdate
    data class Target(val target: LatLng, val zoom: Float? = null) : CameraUpdate
    data class Bounds(val bounds: LatLngBounds, val padding: Int, val width: Int? = null, val height: Int? = null) : CameraUpdate
    data class Scroll(val xPixel: Float, val yPixel: Float) : CameraUpdate
    data class Zoom(val amount: Float, val absolute: Boolean, val focus: Point? = null) : CameraUpdate
}

object CameraUpdateFactory {
    fun newCameraPosition(cameraPosition: CameraPosition): CameraUpdate = CameraUpdate.Position(cameraPosition)
    fun newLatLng(latLng: LatLng): CameraUpdate = CameraUpdate.Target(latLng)
    fun newLatLngZoom(latLng: LatLng, zoom: Float): CameraUpdate = CameraUpdate.Target(latLng, zoom)
    fun newLatLngBounds(bounds: LatLngBounds, padding: Int): CameraUpdate {
        require(padding >= 0)
        return CameraUpdate.Bounds(bounds, padding)
    }
    fun newLatLngBounds(bounds: LatLngBounds, width: Int, height: Int, padding: Int): CameraUpdate {
        require(padding >= 0 && width > padding * 2 && height > padding * 2)
        return CameraUpdate.Bounds(bounds, padding, width, height)
    }
    fun scrollBy(xPixel: Float, yPixel: Float): CameraUpdate = CameraUpdate.Scroll(xPixel, yPixel)
    fun zoomIn(): CameraUpdate = zoomBy(1f)
    fun zoomOut(): CameraUpdate = zoomBy(-1f)
    fun zoomTo(zoom: Float): CameraUpdate = CameraUpdate.Zoom(zoom, true)
    fun zoomBy(amount: Float): CameraUpdate = CameraUpdate.Zoom(amount, false)
    fun zoomBy(amount: Float, focus: Point): CameraUpdate = CameraUpdate.Zoom(amount, false, Point(focus.x, focus.y))
}
