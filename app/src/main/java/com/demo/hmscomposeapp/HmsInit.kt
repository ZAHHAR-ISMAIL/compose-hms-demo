package com.demo.hmscomposeapp

import android.Manifest
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.huawei.hmf.tasks.OnCompleteListener
import com.huawei.hmf.tasks.OnFailureListener
import com.huawei.hmf.tasks.OnSuccessListener
import com.huawei.hms.common.ApiException
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.*
import com.huawei.hms.maps.MapsInitializer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class HmsInit(mainActivity: MainActivity) {

    val mainActivity = mainActivity
    var settingsClient = LocationServices.getSettingsClient(mainActivity)

    // Define a fusedLocationProviderClient object.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient




    companion object {
        private const val TAG = "HMS--LOCATION"
    }

    fun hmsMapInit() {
        MapsInitializer.initialize(mainActivity)
    }

     fun checkLocationPermissions(): Boolean {
        val fineLocationPermission = ActivityCompat.checkSelfPermission(
            mainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ActivityCompat.checkSelfPermission(
            mainActivity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationPermission && coarseLocationPermission
    }

     fun requestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        ActivityCompat.requestPermissions(mainActivity, permissions, 1)
    }


     fun chekLocationSettings() {

        val builder = LocationSettingsRequest.Builder()
        var mLocationRequest = LocationRequest()

        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()

        // Check the device location settings.
        settingsClient.checkLocationSettings(locationSettingsRequest)
            // Define the listener for success in calling the API for checking device location settings.
            .addOnSuccessListener(OnSuccessListener { locationSettingsResponse ->
                val locationSettingsStates = locationSettingsResponse.locationSettingsStates
                val stringBuilder = StringBuilder()
                // Check whether the location function is enabled.
                stringBuilder.append("isLocationUsable=")
                    .append(locationSettingsStates.isLocationUsable)
                // Check whether HMS Core (APK) is available.
                stringBuilder.append(",\nisHMSLocationUsable=")
                    .append(locationSettingsStates.isHMSLocationUsable)
                Log.i(TAG, "checkLocationSetting onComplete:$stringBuilder")
            })
            .addOnFailureListener(OnFailureListener { e ->
                // Processing when the device is a Huawei device and has HMS Core (APK) installed, but its settings do not meet the location requirements.
                val statusCode: Int = (e as ApiException).getStatusCode()
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val rae: ResolvableApiException = e as ResolvableApiException
                        // Call startResolutionForResult to display a popup asking the user to enable related permission.
                        rae.startResolutionForResult(mainActivity, 0)
                    } catch (sie: SendIntentException) {
                        // TODO
                    }
                }
            })
    }


      fun getHmsLocation(){
          fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(mainActivity)
          val lastLocation =
              fusedLocationProviderClient.lastLocation
          lastLocation.addOnSuccessListener(OnSuccessListener { location ->
              if (location == null) {
                  return@OnSuccessListener
              }

              Log.d(TAG, "lat : " + location.latitude + " long : " + location.longitude)
              // TODO: Define logic for processing the Location object upon success.
              return@OnSuccessListener
          })
              // Define callback for failure in obtaining the last known location.
              .addOnFailureListener {
                  // TODO: Define callback for API call failure.
              }


      }

    private class CheckSettingsRequest {
        var locationRequest: LocationRequest = LocationRequest()
        var isAlwaysShow = false
        var isNeedBle = false

    }

}