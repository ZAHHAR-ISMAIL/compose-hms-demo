package com.demo.hmscomposeapp.map

import com.demo.hmscomposeapp.locationpicker.LatLongitude

interface LocationProvider {
    /** Caller owns foreground permission. Cancellation releases location updates. */
    suspend fun currentLocation(): LatLongitude
}
