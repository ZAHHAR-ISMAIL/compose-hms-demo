package com.demo.maps.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.demo.maps.CameraUpdate
import com.demo.maps.backend.CameraController
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class CameraMoveStartedReason { UNKNOWN, GESTURE, API_ANIMATION, DEVELOPER_ANIMATION }

/** Main-thread state for one map. Commands wait for a laid-out map and cancellation is propagated. */
@Stable
class CameraPositionState(position: CameraPosition = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 0f)) {
    private var rawPosition by mutableStateOf(position)
    var position: CameraPosition
        get() = rawPosition
        set(value) { rawPosition = value; move(CameraUpdate.Position(value)) }
    var isMoving: Boolean by mutableStateOf(false)
        private set
    var cameraMoveStartedReason by mutableStateOf(CameraMoveStartedReason.UNKNOWN)
        private set
    private var owner: Any? = null
    private var controller: CameraController? = null
    private var available = CompletableDeferred<CameraController>()
    private var pendingMove: CameraUpdate? = null
    private var animation: Deferred<Unit>? = null

    fun move(update: CameraUpdate) {
        animation?.cancel(CancellationException("Camera moved"))
        controller?.let { it.move(update) } ?: run { pendingMove = update }
        if (update is CameraUpdate.Position) rawPosition = update.position
        if (update is CameraUpdate.Target) rawPosition = rawPosition.copy(target = update.target, zoom = update.zoom ?: rawPosition.zoom)
    }

    suspend fun animate(update: CameraUpdate, durationMs: Int = Int.MAX_VALUE): Unit = coroutineScope {
        require(durationMs > 0) { "Animation duration must be positive" }
        animation?.cancel(CancellationException("New camera animation"))
        val task = async {
            val ready = controller ?: available.await()
            ready.animate(update, durationMs)
        }
        animation = task
        try { task.await() } finally { if (animation === task) animation = null }
    }

    /** Backend lifecycle hooks; applications should use move/animate instead. */
    fun bind(owner: Any) {
        check(this.owner == null || this.owner === owner) { "CameraPositionState may only belong to one map at a time" }
        this.owner = owner
    }
    fun attach(owner: Any, controller: CameraController) {
        bind(owner)
        check(this.controller == null || this.controller === controller)
        controller.move(pendingMove ?: CameraUpdate.Position(rawPosition))
        pendingMove = null
        this.controller = controller
        available.complete(controller)
    }
    fun detach(owner: Any) {
        if (this.owner !== owner) return
        animation?.cancel(CancellationException("Map disposed"))
        controller?.stopAnimation()
        controller = null
        available.cancel(CancellationException("Map disposed"))
        available = CompletableDeferred()
        this.owner = null
        isMoving = false
    }
    fun updateFromBackend(position: CameraPosition, isMoving: Boolean, reason: CameraMoveStartedReason = cameraMoveStartedReason) {
        rawPosition = position
        this.isMoving = isMoving
        cameraMoveStartedReason = reason
    }

    companion object {
        val Saver = Saver<CameraPositionState, List<Any>>(
            save = { listOf(it.position.target.latitude, it.position.target.longitude, it.position.zoom, it.position.tilt, it.position.bearing) },
            restore = { CameraPositionState(CameraPosition(LatLng(it[0] as Double, it[1] as Double), it[2] as Float, it[3] as Float, it[4] as Float)) },
        )
    }
}

@Composable
fun rememberCameraPositionState(key: String? = null, init: CameraPositionState.() -> Unit = {}): CameraPositionState =
    rememberSaveable(key, saver = CameraPositionState.Saver) { CameraPositionState().apply(init) }

@Stable
class MarkerState(position: LatLng = LatLng(0.0, 0.0)) {
    var position by mutableStateOf(position)
    var isDragging by mutableStateOf(false)
        private set
    private var owner: Any? = null
    private var show: () -> Unit = {}
    private var hide: () -> Unit = {}
    fun showInfoWindow() = show()
    fun hideInfoWindow() = hide()
    fun bind(owner: Any, show: () -> Unit, hide: () -> Unit) {
        check(this.owner == null || this.owner === owner) { "MarkerState may only belong to one marker at a time" }
        this.owner = owner; this.show = show; this.hide = hide
    }
    fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null; show = {}; hide = {}; isDragging = false
    }
    fun updateFromBackend(position: LatLng, isDragging: Boolean) {
        this.isDragging = isDragging
        this.position = position
    }
    companion object {
        val Saver = Saver<MarkerState, List<Double>>(
            save = { listOf(it.position.latitude, it.position.longitude) },
            restore = { MarkerState(LatLng(it[0], it[1])) },
        )
    }
}

@Composable
fun rememberMarkerState(key: String? = null, position: LatLng = LatLng(0.0, 0.0)): MarkerState =
    rememberSaveable(key, saver = MarkerState.Saver) { MarkerState(position) }

@Composable
fun rememberUpdatedMarkerState(position: LatLng = LatLng(0.0, 0.0)): MarkerState =
    remember { MarkerState(position) }.also { it.position = position }
