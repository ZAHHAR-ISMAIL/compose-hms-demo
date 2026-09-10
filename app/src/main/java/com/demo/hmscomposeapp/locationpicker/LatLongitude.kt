package com.demo.hmscomposeapp.locationpicker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LatLongitude(val latitude: Double = 0.0, val longitude: Double = 0.0) : Parcelable
