package com.demo.hmscomposeapp.map

import android.content.Context
import com.demo.maps.backend.MapBackend
import com.demo.maps.google.GoogleMapBackend

fun createMapBackend(): MapBackend = GoogleMapBackend
fun createLocationProvider(context: Context): LocationProvider = PlatformLocationProvider(context)
