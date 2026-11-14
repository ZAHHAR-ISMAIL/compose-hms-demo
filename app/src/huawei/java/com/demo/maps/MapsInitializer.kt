package com.demo.maps

import android.content.Context

/** Direct huawei SDK initialization, selected by the build flavor. No registration is required. */
object MapsInitializer {
    fun initialize(context: Context) {
        com.huawei.hms.maps.MapsInitializer.initialize(context.applicationContext)
    }
}
