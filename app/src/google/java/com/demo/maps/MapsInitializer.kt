package com.demo.maps

import android.content.Context

/** Direct google SDK initialization, selected by the build flavor. No registration is required. */
object MapsInitializer {
    fun initialize(context: Context) {
        com.google.android.gms.maps.MapsInitializer.initialize(context.applicationContext)
    }
}
