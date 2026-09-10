package com.demo.hmscomposeapp.locationpicker

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedAddress(
    val address: String,
    val city: String?,
    val region: String?,
    val country: String?,
)

object LocationAddressResolver {
    suspend fun resolve(context: Context, coordinate: LatLongitude): ResolvedAddress {
        val fallback = "${coordinate.latitude}, ${coordinate.longitude}"
        if (!Geocoder.isPresent()) return ResolvedAddress(fallback, null, null, null)

        val address = withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
            lookup(context, coordinate)
        } ?: return ResolvedAddress(fallback, null, null, null)

        val line = (0..address.maxAddressLineIndex)
            .mapNotNull(address::getAddressLine)
            .joinToString(", ")
            .ifBlank { fallback }
        return ResolvedAddress(
            address = line,
            city = address.locality ?: address.subAdminArea,
            region = address.adminArea ?: address.subLocality,
            country = address.countryName,
        )
    }

    private suspend fun lookup(context: Context, coordinate: LatLongitude): Address? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                runCatching {
                    geocoder.getFromLocation(
                        coordinate.latitude,
                        coordinate.longitude,
                        1,
                        listener,
                    )
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching {
                    geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                        ?.firstOrNull()
                }.getOrNull()
            }
        }
    }
}

private const val GEOCODER_TIMEOUT_MS = 10_000L
