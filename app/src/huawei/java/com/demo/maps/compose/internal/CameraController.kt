package com.demo.maps.compose.internal

import com.demo.maps.CameraUpdate

/**
 * HMS-only command receiver used by Huawei's compatibility state. Methods run on the UI thread.
 * Google uses the official CameraPositionState directly and does not implement this interface.
 */
internal interface CameraController {
    fun move(update: CameraUpdate)
    suspend fun animate(update: CameraUpdate, durationMs: Int)
    fun stopAnimation()
}
