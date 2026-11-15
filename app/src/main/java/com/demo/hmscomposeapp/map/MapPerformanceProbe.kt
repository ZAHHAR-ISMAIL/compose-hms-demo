package com.demo.hmscomposeapp.map

/** Optional demo/test counters. Disabled during ordinary app use; never stores SDK objects. */
internal object MapPerformanceProbe {
    var enabled = false
    var bitmapFrames = 0L
    var markerCreations = 0L
    var iconRequests = 0L
    fun reset() { bitmapFrames = 0; markerCreations = 0; iconRequests = 0 }
    fun bitmap() { if (enabled) bitmapFrames++ }
    fun marker(icon: Boolean) { if (enabled) { markerCreations++; if (icon) iconRequests++ } }
    fun icon() { if (enabled) iconRequests++ }
}
