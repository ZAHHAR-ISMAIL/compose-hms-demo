package com.demo.maps

import com.demo.maps.compose.internal.CameraController
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.MarkerState
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraStateTest {
    private class Controller : CameraController {
        val moves = mutableListOf<CameraUpdate>()
        val animations = mutableListOf<CameraUpdate>()
        var waitForever = false
        var stopped = false
        override fun move(update: CameraUpdate) { moves += update }
        override suspend fun animate(update: CameraUpdate, durationMs: Int) {
            animations += update
            if (waitForever) awaitCancellation()
        }
        override fun stopAnimation() { stopped = true }
    }

    @Test fun lastMoveBeforeMapReadinessWins() {
        val state = CameraPositionState()
        val controller = Controller()
        state.move(CameraUpdateFactory.newLatLngZoom(LatLng(24.7, 46.6), 10f))
        val final = CameraUpdateFactory.newLatLngZoom(LatLng(24.8, 46.7), 15f)
        state.move(final)
        state.attach(controller, controller)
        assertEquals(listOf(final), controller.moves)
    }
    @Test fun animateWaitsForReadinessAndCompletes() = runTest {
        val state = CameraPositionState()
        val controller = Controller()
        val update = CameraUpdateFactory.zoomTo(15f)
        val animation = async { state.animate(update, 100) }
        runCurrent()
        assertFalse(animation.isCompleted)
        state.attach(controller, controller)
        animation.await()
        assertEquals(listOf(update), controller.animations)
    }
    @Test fun moveCancelsPendingAnimation() = runTest {
        val state = CameraPositionState()
        val animation = async { state.animate(CameraUpdateFactory.zoomTo(15f)) }
        runCurrent()
        state.move(CameraUpdateFactory.zoomTo(10f))
        runCurrent()
        assertTrue(animation.isCancelled)
    }
    @Test fun disposalCancelsActiveAnimationAndStopsNativeCamera() = runTest {
        val state = CameraPositionState()
        val controller = Controller().apply { waitForever = true }
        state.attach(controller, controller)
        val animation = async { state.animate(CameraUpdateFactory.zoomTo(15f)) }
        runCurrent()
        state.detach(controller)
        runCurrent()
        assertTrue(animation.isCancelled)
        assertTrue(controller.stopped)
    }
    @Test fun disposalCancelsAnimationWaitingForLayout() = runTest {
        val state = CameraPositionState()
        val owner = Any()
        state.bind(owner)
        val animation = async { state.animate(CameraUpdateFactory.zoomTo(15f)) }
        runCurrent()
        state.detach(owner)
        runCurrent()
        assertTrue(animation.isCancelled)
    }
    @Test fun newAnimationCancelsPreviousAnimation() = runTest {
        val state = CameraPositionState()
        val controller = Controller().apply { waitForever = true }
        state.attach(controller, controller)
        val first = async { state.animate(CameraUpdateFactory.zoomTo(15f)) }
        runCurrent()
        controller.waitForever = false
        val second = async { state.animate(CameraUpdateFactory.zoomTo(12f)) }
        second.await()
        assertTrue(first.isCancelled)
        assertEquals(2, controller.animations.size)
    }
    @Test(expected = IllegalStateException::class) fun aCameraStateCannotBelongToTwoMaps() {
        CameraPositionState().apply { bind(Any()); bind(Any()) }
    }
    @Test fun cameraStateCanBeReusedAfterDisposal() {
        val state = CameraPositionState()
        val first = Controller()
        state.attach(first, first); state.detach(first)
        val second = Controller()
        state.attach(second, second)
        assertEquals(1, second.moves.size)
    }
    @Test fun cameraSaverContainsOnlyScalarValues() {
        val state = CameraPositionState(CameraPosition(LatLng(24.7, 46.6), 15f, 30f, 90f))
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(CameraPositionState.Saver) { scope.save(state) }
        val restored = CameraPositionState.Saver.restore(checkNotNull(saved))
        assertEquals(state.position, restored?.position)
    }
    @Test fun markerUpdatesAndOwnershipAreIndependentOfProvider() {
        val state = MarkerState(LatLng(24.7, 46.6))
        val owner = Any()
        var shown = false
        state.bind(owner, { shown = true }, { shown = false })
        state.showInfoWindow(); assertTrue(shown)
        state.updateFromMap(LatLng(24.8, 46.7), true)
        assertTrue(state.isDragging)
        state.detach(owner)
        assertFalse(state.isDragging)
        state.bind(Any(), {}, {})
    }
}
