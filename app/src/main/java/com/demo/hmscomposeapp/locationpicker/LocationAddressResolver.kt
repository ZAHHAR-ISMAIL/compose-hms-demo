package com.demo.hmscomposeapp.locationpicker

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ResolvedAddress(
    val address: String,
    val city: String?,
    val region: String?,
    val country: String?
)

object LocationAddressResolver {

    suspend fun resolve(context: Context, latLongitude: LatLongitude): ResolvedAddress {
        return withContext(Dispatchers.IO) {
            val fallbackAddress = "${latLongitude.latitude}, ${latLongitude.longitude}"
            if (!Geocoder.isPresent()) {
                return@withContext ResolvedAddress(
                    address = fallbackAddress,
                    city = null,
                    region = null,
                    country = null
                )
            }

            runCatching {
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(latLongitude.latitude, latLongitude.longitude, 1)
                    ?.firstOrNull()
            }.getOrNull()?.let { address ->
                val line = (0..address.maxAddressLineIndex)
                    .mapNotNull(address::getAddressLine)
                    .joinToString(", ")
                    .ifBlank { fallbackAddress }

                ResolvedAddress(
                    address = line,
                    city = address.locality ?: address.subAdminArea,
                    region = address.adminArea ?: address.subLocality,
                    country = address.countryName
                )
            } ?: ResolvedAddress(
                address = fallbackAddress,
                city = null,
                region = null,
                country = null
            )
        }
    }
}
