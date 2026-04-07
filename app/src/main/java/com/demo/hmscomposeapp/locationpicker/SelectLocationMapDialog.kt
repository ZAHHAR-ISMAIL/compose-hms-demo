package com.demo.hmscomposeapp.locationpicker

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.demo.hmscomposeapp.R
import com.huawei.hms.maps.CameraUpdateFactory
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.SupportMapFragment
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import com.huawei.hms.maps.model.LatLng as HuaweiLatLng
import kotlinx.coroutines.launch

class SelectLocationMapDialog : DialogFragment(R.layout.h_map) {

    private var huaweiMap: HuaweiMap? = null
    private var marker: Marker? = null
    private var currentSelection: LocationDetails? = null

    private lateinit var selectedAddressView: TextView
    private lateinit var selectedMetaView: TextView
    private lateinit var confirmButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSelection = arguments?.parcelable(Constants.LOCATION_DETAILS_PARAM)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedAddressView = view.findViewById(R.id.selectedAddressText)
        selectedMetaView = view.findViewById(R.id.selectedMetaText)
        confirmButton = view.findViewById(R.id.confirmLocationButton)

        view.findViewById<Button>(R.id.cancelLocationButton).setOnClickListener {
            dismiss()
        }
        confirmButton.setOnClickListener {
            confirmSelection()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.huaweiMapFragment)
            as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also { fragment ->
                childFragmentManager.beginTransaction()
                    .replace(R.id.huaweiMapFragment, fragment)
                    .commitNow()
            }

        mapFragment.getMapAsync { map ->
            huaweiMap = map
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isZoomControlsEnabled = true
            map.setOnMapClickListener(::onMapTapped)
            renderInitialSelection()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun renderInitialSelection() {
        val target = currentSelection?.latLng ?: LatLongitude(
            latitude = Constants.DEFAULT_LATITUDE,
            longitude = Constants.DEFAULT_LONGITUDE
        )
        updateMapSelection(target.toHuaweiLatLng(), moveCamera = true)

        if (currentSelection?.address.isNullOrBlank()) {
            resolveSelection(target)
        } else {
            updateAddressViews(currentSelection!!)
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
        if (moveCamera || currentSelection?.latLng == null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    private fun resolveSelection(latLongitude: LatLongitude) {
        confirmButton.isEnabled = false
        selectedAddressView.text = "Resolving address..."
        selectedMetaView.text = ""

        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = LocationAddressResolver.resolve(
                context = requireContext(),
                latLongitude = latLongitude
            )
            currentSelection = LocationDetails(
                latLng = latLongitude,
                address = resolved.address,
                city = resolved.city,
                region = resolved.region,
                country = resolved.country
            )
            currentSelection?.let(::updateAddressViews)
            confirmButton.isEnabled = true
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

    private fun confirmSelection() {
        val selection = currentSelection ?: return
        parentFragmentManager.setFragmentResult(
            Constants.SELECT_LOCATION_RESULT_KEY,
            bundleOf(Constants.PLACE_PARAM to selection)
        )
        dismiss()
    }

    companion object {
        fun newInstance(locationDetails: LocationDetails): SelectLocationMapDialog {
            return SelectLocationMapDialog().apply {
                arguments = bundleOf(Constants.LOCATION_DETAILS_PARAM to locationDetails)
            }
        }
    }
}
