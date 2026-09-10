package com.demo.hmscomposeapp.map

import android.annotation.SuppressLint
import android.content.Context
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PlatformLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LatLongitude = withTimeout(LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(MAX_CACHED_LOCATION_AGE_MS)
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()

            continuation.invokeOnCancellation { cancellation.cancel() }
            client.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (location == null) {
                        continuation.resumeWithException(
                            IllegalStateException("No current location is available"),
                        )
                    } else {
                        continuation.resume(LatLongitude(location.latitude, location.longitude))
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }
}

private const val LOCATION_TIMEOUT_MS = 15_000L
private const val MAX_CACHED_LOCATION_AGE_MS = 30_000L
