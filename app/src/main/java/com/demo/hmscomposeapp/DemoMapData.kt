package com.demo.hmscomposeapp

import com.demo.hmscomposeapp.locationpicker.Constants
import com.demo.hmscomposeapp.locationpicker.LatLongitude

internal object DemoMapData {
    val start = LatLongitude(Constants.DEFAULT_LATITUDE, Constants.DEFAULT_LONGITUDE)

    // Static OSRM route from Kingdom Centre to King Fahd Library in Riyadh.
    val route = listOf(
        LatLongitude(24.713630, 46.675293),
        LatLongitude(24.713530, 46.675035),
        LatLongitude(24.713393, 46.675099),
        LatLongitude(24.710543, 46.676544),
        LatLongitude(24.709310, 46.677137),
        LatLongitude(24.704950, 46.679319),
        LatLongitude(24.699426, 46.682145),
        LatLongitude(24.698205, 46.682732),
        LatLongitude(24.696670, 46.683518),
        LatLongitude(24.695595, 46.684105),
        LatLongitude(24.694651, 46.684546),
        LatLongitude(24.693506, 46.685151),
        LatLongitude(24.692638, 46.685567),
        LatLongitude(24.689596, 46.687166),
        LatLongitude(24.688718, 46.684974),
        LatLongitude(24.687677, 46.685494),
        LatLongitude(24.687747, 46.685678),
    )
}
