package com.demo.hmscomposeapp.locationpicker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FiberEligibilityResponse(
    val isEligible: Boolean = false,
    val message: String? = null
) : Parcelable
