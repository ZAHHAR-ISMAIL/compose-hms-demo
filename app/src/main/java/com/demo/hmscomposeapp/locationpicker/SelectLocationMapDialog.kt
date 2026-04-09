package com.demo.hmscomposeapp.locationpicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.demo.hmscomposeapp.R
import com.huawei.hms.maps.CameraUpdateFactory
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapView
import com.huawei.hms.maps.model.LatLng as HuaweiLatLng
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SelectLocationMapDialog(
    context: Context,
    private val initialLocation: LocationDetails,
    private val onLocationConfirmed: (LocationDetails) -> Unit
) : Dialog(context) {

    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mapView: MapView
    private lateinit var selectedAddressView: TextView
    private lateinit var selectedMetaView: TextView
    private lateinit var confirmButton: Button

    private var huaweiMap: HuaweiMap? = null
    private var marker: Marker? = null
    private var currentSelection: LocationDetails = initialLocation
    private var mapLifecycleReleased = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.h_map)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        mapView = findViewById(R.id.huaweiMapView)
        selectedAddressView = findViewById(R.id.selectedAddressText)
        selectedMetaView = findViewById(R.id.selectedMetaText)
        confirmButton = findViewById(R.id.confirmLocationButton)

        findViewById<Button>(R.id.cancelLocationButton).setOnClickListener {
            dismiss()
        }
        confirmButton.setOnClickListener {
            onLocationConfirmed(currentSelection)
            dismiss()
        }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            huaweiMap = map
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isZoomControlsEnabled = true
            map.setOnMapClickListener(::onMapTapped)
            renderInitialSelection()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        mapView.onResume()
    }

    override fun onStop() {
        mapView.onPause()
        mapView.onStop()
        super.onStop()
    }

    override fun dismiss() {
        releaseMapLifecycleIfNeeded()
        dialogScope.cancel()
        super.dismiss()
    }

    private fun renderInitialSelection() {
        val target = currentSelection.latLng ?: LatLongitude(
            latitude = Constants.DEFAULT_LATITUDE,
            longitude = Constants.DEFAULT_LONGITUDE
        )
        updateMapSelection(target.toHuaweiLatLng(), moveCamera = true)

        if (currentSelection.address.isNullOrBlank()) {
            resolveSelection(target)
        } else {
            updateAddressViews(currentSelection)
        }
    }

    private fun onMapTapped(latLng: HuaweiLatLng) {
        updateMapSelection(latLng, moveCamera = false)
        resolveSelection(LatLongitude.fromHuaweiLatLng(latLng))
    }

    private fun updateMapSelection(latLng: HuaweiLatLng, moveCamera: Boolean) {
        val map = huaweiMap ?: return
        if (marker == null) {
            marker = map.addMarker(MarkerOptions().position(latLng))
        } else {
            marker?.position = latLng
        }
        if (moveCamera || currentSelection.latLng == null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    private fun resolveSelection(latLongitude: LatLongitude) {
        confirmButton.isEnabled = false
        selectedAddressView.text = "Resolving address..."
        selectedMetaView.text = ""

        dialogScope.launch {
            val resolved = LocationAddressResolver.resolve(
                context = context,
                latLongitude = latLongitude
            )
            currentSelection = LocationDetails(
                latLng = latLongitude,
                address = resolved.address,
                city = resolved.city,
                region = resolved.region,
                country = resolved.country
            )
            updateAddressViews(currentSelection)
        }
    }

    private fun updateAddressViews(location: LocationDetails) {
        selectedAddressView.text = location.address ?: "No address selected."
        selectedMetaView.text = listOfNotNull(
            location.city,
            location.region,
            location.country
        ).joinToString(", ")
        confirmButton.isEnabled = location.latLng != null
    }

    private fun releaseMapLifecycleIfNeeded() {
        if (!::mapView.isInitialized || mapLifecycleReleased) return
        mapLifecycleReleased = true
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
    }
}
