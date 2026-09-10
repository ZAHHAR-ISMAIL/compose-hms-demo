package com.demo.hmscomposeapp.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.huawei.hms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

internal class PlatformLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LatLongitude = withTimeout(15_000) {
        val cached = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            client.lastLocation.addOnSuccessListener {
                if (continuation.isActive) continuation.resume(it)
            }.addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        if (cached?.isFresh() == true) {
            return@withTimeout LatLongitude(cached.latitude, cached.longitude)
        }
        suspendCancellableCoroutine { continuation ->
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    client.removeLocationUpdates(this)
                    if (continuation.isActive) continuation.resume(LatLongitude(location.latitude, location.longitude))
                }
            }
            continuation.invokeOnCancellation { client.removeLocationUpdates(callback) }
            val request = LocationRequest().apply {
                interval = 1000
                fastestInterval = 500
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {
                    // Registration may finish after cancellation; remove again in that case.
                    if (!continuation.isActive) client.removeLocationUpdates(callback)
                }.addOnFailureListener {
                    client.removeLocationUpdates(callback)
                    if (continuation.isActive) continuation.resumeWithException(it)
                }
        }
    }
}

private fun android.location.Location.isFresh(): Boolean {
    val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
    return ageNanos >= 0 && TimeUnit.NANOSECONDS.toMillis(ageNanos) <= MAX_CACHED_LOCATION_AGE_MS
}

private const val MAX_CACHED_LOCATION_AGE_MS = 30_000L
