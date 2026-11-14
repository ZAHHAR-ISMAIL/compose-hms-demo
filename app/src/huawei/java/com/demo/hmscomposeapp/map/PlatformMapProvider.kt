package com.demo.hmscomposeapp.map

import android.content.Context
import com.demo.maps.backend.MapBackend
import com.demo.maps.huawei.HuaweiMapBackend

fun createMapBackend(): MapBackend = HuaweiMapBackend
fun createLocationProvider(context: Context): LocationProvider = PlatformLocationProvider(context)
