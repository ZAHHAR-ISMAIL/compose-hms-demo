package com.demo.maps.compose

import androidx.compose.runtime.Composable
import com.demo.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState as nativeRememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState as nativeRememberMarkerState
import com.google.maps.android.compose.rememberUpdatedMarkerState as nativeRememberUpdatedMarkerState

/**
 * A screen owns the official Google state object itself. There is no second state object,
 * custom camera queue, controller, or snapshotFlow copying values back and forth on this flavor.
 * Ownership, gestures, animation cancellation and saving are implemented by Maps Compose.
 */
typealias CameraPositionState = com.google.maps.android.compose.CameraPositionState
typealias CameraMoveStartedReason = com.google.maps.android.compose.CameraMoveStartedReason
typealias MarkerState = com.google.maps.android.compose.MarkerState

/**
 * Top-level functions cannot be typealiased, so forward to the official remember factory.
 * The native CameraPositionState.Saver is retained. The optional key overload is supported for
 * existing callers, although the pinned Google library deprecates explicit saveable keys.
 */
@Suppress("DEPRECATION")
@Composable
fun rememberCameraPositionState(
    key: String? = null,
    init: CameraPositionState.() -> Unit = {},
): CameraPositionState = if (key == null) {
    nativeRememberCameraPositionState(init = init)
} else {
    nativeRememberCameraPositionState(key = key, init = init)
}

/** Forward saveable marker creation to Google; no compatibility MarkerState is allocated. */
@Suppress("DEPRECATION")
@Composable
fun rememberMarkerState(key: String? = null, position: LatLng = LatLng(0.0, 0.0)): MarkerState =
    nativeRememberMarkerState(key = key, position = position)

/** Like Google's function, this follows the position input and is not a saved-state factory. */
@Composable
fun rememberUpdatedMarkerState(position: LatLng = LatLng(0.0, 0.0)): MarkerState =
    nativeRememberUpdatedMarkerState(position = position)
