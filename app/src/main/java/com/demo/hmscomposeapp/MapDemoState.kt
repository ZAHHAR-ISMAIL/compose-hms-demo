package com.demo.hmscomposeapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.demo.hmscomposeapp.locationpicker.LocationAddressResolver
import com.demo.hmscomposeapp.map.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

internal enum class LocationError {
    PermissionDenied,
    Timeout,
    Unavailable,
}

@Stable
internal class MapDemoState(
    private val appContext: Context,
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope,
    selected: LatLongitude = DemoMapData.start,
    picking: Boolean = false,
    draft: LatLongitude = selected,
) {
    var selected by mutableStateOf(selected)
        private set

    var isPicking by mutableStateOf(picking)
        private set

    var draft by mutableStateOf(draft)
        private set

    var address by mutableStateOf<String?>(null)
        private set

    var isResolvingAddress by mutableStateOf(false)
        private set

    var isLocating by mutableStateOf(false)
        private set

    var locationError by mutableStateOf<LocationError?>(null)
        private set

    private var addressJob: Job? = null
    private var locationJob: Job? = null
    private var locationRequestId = 0L

    fun start() {
        if (isPicking) resolveAddress()
    }

    fun openPicker() {
        draft = selected
        isPicking = true
        locationError = null
        resolveAddress()
    }

    fun selectOnMap(coordinate: LatLongitude) {
        cancelLocationRequest()
        updateDraft(coordinate)
    }

    fun useCurrentLocation() {
        locationJob?.cancel()
        val requestId = ++locationRequestId
        isLocating = true
        locationError = null
        locationJob = scope.launch {
            try {
                val coordinate = locationProvider.currentLocation()
                if (requestId == locationRequestId) updateDraft(coordinate)
            } catch (_: TimeoutCancellationException) {
                if (requestId == locationRequestId) locationError = LocationError.Timeout
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (requestId == locationRequestId) locationError = LocationError.Unavailable
            } finally {
                if (requestId == locationRequestId) {
                    isLocating = false
                    locationJob = null
                }
            }
        }
    }

    fun permissionDenied() {
        locationError = LocationError.PermissionDenied
    }

    fun confirmPicker() {
        selected = draft
        closePicker()
    }

    fun cancelPicker() {
        draft = selected
        closePicker()
    }

    fun close() {
        locationRequestId++
        locationJob?.cancel()
        addressJob?.cancel()
    }

    private fun closePicker() {
        cancelLocationRequest()
        addressJob?.cancel()
        isResolvingAddress = false
        isPicking = false
        locationError = null
    }

    private fun cancelLocationRequest() {
        locationRequestId++
        locationJob?.cancel()
        locationJob = null
        isLocating = false
    }

    private fun updateDraft(coordinate: LatLongitude) {
        draft = coordinate
        locationError = null
        resolveAddress()
    }

    private fun resolveAddress() {
        addressJob?.cancel()
        val coordinate = draft
        isResolvingAddress = true
        address = null
        addressJob = scope.launch {
            val resolved = LocationAddressResolver.resolve(appContext, coordinate)
            if (isPicking && draft == coordinate) {
                address = resolved.address
                isResolvingAddress = false
            }
        }
    }
}

@Composable
internal fun rememberMapDemoState(locationProvider: LocationProvider): MapDemoState {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val saver = remember(appContext, locationProvider, scope) {
        listSaver<MapDemoState, Any>(
            save = {
                listOf(
                    it.selected.latitude,
                    it.selected.longitude,
                    it.isPicking,
                    it.draft.latitude,
                    it.draft.longitude,
                )
            },
            restore = {
                MapDemoState(
                    appContext = appContext,
                    locationProvider = locationProvider,
                    scope = scope,
                    selected = LatLongitude(it[0] as Double, it[1] as Double),
                    picking = it[2] as Boolean,
                    draft = LatLongitude(it[3] as Double, it[4] as Double),
                )
            },
        )
    }
    val state = rememberSaveable(locationProvider, saver = saver) {
        MapDemoState(appContext, locationProvider, scope)
    }
    DisposableEffect(state) {
        state.start()
        onDispose(state::close)
    }
    return state
}
