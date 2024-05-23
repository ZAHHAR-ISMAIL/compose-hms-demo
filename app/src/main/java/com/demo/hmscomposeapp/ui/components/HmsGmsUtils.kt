package com.demo.hmscomposeapp.ui.components

import android.content.Context
import android.util.Log
import com.google.android.gms.common.GoogleApiAvailability
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability

object HmsGmsUtil {
    private const val TAG = "HmsGmsUtil"
    private fun isHmsAvailable(context: Context?): Boolean {
        var isAvailable = false
        if (null != context) {
            val result =
                HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context)
            isAvailable = ConnectionResult.SUCCESS == result
        }
        Log.i(TAG, "isHmsAvailable: $isAvailable")
        return isAvailable
    }

    private fun isGmsAvailable(context: Context?): Boolean {
        var isAvailable = false
        if (null != context) {
            val result: Int =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            isAvailable = com.google.android.gms.common.ConnectionResult.SUCCESS == result
        }
        Log.i(TAG, "isGmsAvailable: $isAvailable")
        return isAvailable
    }

    // Check case of Device have only HMS
    fun isOnlyHms(context: Context?): Boolean {
        return isHmsAvailable(context) && !isGmsAvailable(context)
    }
}
