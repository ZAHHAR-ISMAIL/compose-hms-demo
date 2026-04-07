package com.demo.hmscomposeapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.huawei.hms.common.ApiException
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationAvailability
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices
import com.huawei.hms.location.LocationSettingsRequest
import com.huawei.hms.location.LocationSettingsStatusCodes
import com.huawei.hms.maps.MapsInitializer

class HmsInit(private val activity: MainActivity) {

    private val settingsClient = LocationServices.getSettingsClient(activity)
    private val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(activity)

    fun initializeMaps() {
        MapsInitializer.initialize(activity)
    }

    fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all { permission ->
            ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            LOCATION_PERMISSIONS,
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    fun checkLocationSettings() {
        val locationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        settingsClient.checkLocationSettings(settingsRequest)
            .addOnSuccessListener { response ->
                val states = response.locationSettingsStates
                Log.i(
                    TAG,
                    "checkLocationSetting usable=${states.isLocationUsable}, hmsUsable=${states.isHMSLocationUsable}"
                )
            }
            .addOnFailureListener(::handleLocationSettingsFailure)
    }

    fun getHmsLocation(
        onSuccess: (Location) -> Unit = {},
        onFailure: (Exception?) -> Unit = {}
    ) {
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    logLocation("last", location)
                    onSuccess(location)
                } else {
                    requestSingleLocationUpdate(onSuccess, onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    private fun requestSingleLocationUpdate(
        onSuccess: (Location) -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        val locationRequest = LocationRequest().apply {
            interval = 1000
            fastestInterval = 500
            numUpdates = 1
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        var handled = false
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (handled) return
                handled = true
                fusedLocationProviderClient.removeLocationUpdates(this)

                val location = locationResult?.lastLocation
                if (location == null) {
                    onFailure(null)
                    return
                }

                logLocation("single update", location)
                onSuccess(location)
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
                if (handled || locationAvailability?.isLocationAvailable != false) return
                handled = true
                fusedLocationProviderClient.removeLocationUpdates(this)
                onFailure(null)
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        ).addOnFailureListener { exception ->
            if (!handled) {
                handled = true
                onFailure(exception)
            }
        }
    }

    private fun handleLocationSettingsFailure(exception: Exception) {
        val apiException = exception as? ApiException ?: return
        if (apiException.statusCode != LocationSettingsStatusCodes.RESOLUTION_REQUIRED) return

        val resolvableException = apiException as? ResolvableApiException ?: return
        try {
            resolvableException.startResolutionForResult(activity, LOCATION_SETTINGS_REQUEST_CODE)
        } catch (sendIntentException: Exception) {
            Log.w(TAG, "Unable to resolve location settings", sendIntentException)
        }
    }

    private fun logLocation(source: String, location: Location) {
        Log.d(TAG, "$source lat=${location.latitude} long=${location.longitude}")
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1

        private const val TAG = "HMS--LOCATION"
        private const val LOCATION_SETTINGS_REQUEST_CODE = 0
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
