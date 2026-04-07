package com.demo.hmscomposeapp.locationpicker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocationDetails(
    val latLng: LatLongitude? = null,
    val address: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null
) : Parcelable
