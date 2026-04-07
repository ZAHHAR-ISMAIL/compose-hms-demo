package com.demo.hmscomposeapp.locationpicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices
import kotlinx.coroutines.launch

class SelectLocationFragment : Fragment() {

    private lateinit var selectedAddressView: TextView
    private lateinit var selectedMetaView: TextView
    private lateinit var flowNameView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var confirmButton: Button

    private var selectedLocation: LocationDetails? = null
    private var flowName: String = ""
    private var poid: String? = null
    private var eligibilityResponse: FiberEligibilityResponse? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentLocation()
        } else {
            showLoading(false)
            Toast.makeText(
                requireContext(),
                "Location permission is required to fetch the current location.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLocation = arguments?.parcelable(Constants.LOCATION_DETAILS_PARAM)
        flowName = arguments?.getString(Constants.FLOW_NAME_PARAM).orEmpty()
        poid = arguments?.getString(Constants.POID_PARAM)
        eligibilityResponse = arguments?.parcelable(Constants.ELIGIBILITY_RESPONSE)

        childFragmentManager.setFragmentResultListener(
            Constants.SELECT_LOCATION_RESULT_KEY,
            this
        ) { _, result ->
            result.parcelable<LocationDetails>(Constants.PLACE_PARAM)?.let { location ->
                selectedLocation = location
                renderSelection()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val padding = (16 * resources.displayMetrics.density).toInt()

        flowNameView = TextView(context).apply {
            textSize = 20f
        }
        selectedAddressView = TextView(context).apply {
            textSize = 16f
        }
        selectedMetaView = TextView(context).apply {
            textSize = 14f
        }
        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        val openMapButton = Button(context).apply {
            text = "Open map picker"
            setOnClickListener { showMapDialog() }
        }
        val currentLocationButton = Button(context).apply {
            text = "Use current location"
            setOnClickListener { ensureLocationPermissionAndFetch() }
        }
        confirmButton = Button(context).apply {
            text = "Confirm location"
            isEnabled = false
            setOnClickListener { confirmSelection() }
        }
        val cancelButton = Button(context).apply {
            text = "Cancel"
            setOnClickListener { requireActivity().finish() }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER_HORIZONTAL

            addView(flowNameView)
            addView(spaceView(context))
            addView(selectedAddressView)
            addView(spaceView(context))
            addView(selectedMetaView)
            addView(spaceView(context))
            addView(progressBar)
            addView(spaceView(context))
            addView(openMapButton)
            addView(spaceView(context))
            addView(currentLocationButton)
            addView(spaceView(context))
            addView(confirmButton)
            addView(spaceView(context))
            addView(cancelButton)
        }

        return ScrollView(context).apply {
            addView(content)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderSelection()

        if (selectedLocation?.latLng == null) {
            ensureLocationPermissionAndFetch()
        } else if (selectedLocation?.address.isNullOrBlank()) {
            resolveSelectionAddress()
        }
    }

    private fun showMapDialog() {
        val location = selectedLocation ?: LocationDetails(
            latLng = LatLongitude(
                latitude = Constants.DEFAULT_LATITUDE,
                longitude = Constants.DEFAULT_LONGITUDE
            )
        )

        SelectLocationMapDialog.newInstance(location)
            .show(childFragmentManager, Constants.SELECT_LOCATION_DIALOG_TAG)
    }

    private fun ensureLocationPermissionAndFetch() {
        if (hasLocationPermission()) {
            fetchCurrentLocation()
        } else {
            showLoading(true)
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun fetchCurrentLocation() {
        if (!hasLocationPermission()) return

        showLoading(true)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    applyCoordinates(location.latitude, location.longitude)
                } else {
                    requestSingleLocationUpdate()
                }
            }
            .addOnFailureListener {
                requestSingleLocationUpdate()
            }
    }

    private fun requestSingleLocationUpdate() {
        val locationRequest = LocationRequest().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                fusedLocationClient.removeLocationUpdates(this)
                val location = locationResult?.lastLocation
                if (location == null) {
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        "Unable to fetch current location.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                applyCoordinates(location.latitude, location.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        ).addOnFailureListener {
            showLoading(false)
            Toast.makeText(
                requireContext(),
                "Unable to request location updates.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyCoordinates(latitude: Double, longitude: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = LocationAddressResolver.resolve(
                context = requireContext(),
                latLongitude = LatLongitude(latitude, longitude)
            )
            selectedLocation = LocationDetails(
                latLng = LatLongitude(latitude, longitude),
                address = resolved.address,
                city = resolved.city,
                region = resolved.region,
                country = resolved.country
            )
            renderSelection()
            showLoading(false)
        }
    }

    private fun resolveSelectionAddress() {
        val coordinates = selectedLocation?.latLng ?: return
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = LocationAddressResolver.resolve(
                context = requireContext(),
                latLongitude = coordinates
            )
            selectedLocation = selectedLocation?.copy(
                address = resolved.address,
                city = resolved.city,
                region = resolved.region,
                country = resolved.country
            )
            renderSelection()
            showLoading(false)
        }
    }

    private fun renderSelection() {
        val location = selectedLocation
        flowNameView.text = if (flowName.isBlank()) {
            "Select location"
        } else {
            "Select location for $flowName"
        }

        selectedAddressView.text = location?.address ?: "No location selected yet."
        selectedMetaView.text = buildString {
            location?.latLng?.let {
                append("Lat: ${it.latitude}, Lng: ${it.longitude}")
            }
            val placeMeta = listOfNotNull(location?.city, location?.region, location?.country)
                .joinToString(", ")
            if (placeMeta.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(placeMeta)
            }
            if (!poid.isNullOrBlank()) {
                if (isNotEmpty()) append('\n')
                append("POID: $poid")
            }
        }.ifBlank { "Use the map or your current location to continue." }

        confirmButton.isEnabled = location?.latLng != null
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        confirmButton.isEnabled = !isLoading && selectedLocation?.latLng != null
    }

    private fun confirmSelection() {
        val location = selectedLocation ?: return
        val returnIntent = Intent().apply {
            putExtra(Constants.PLACE_PARAM, location)
            putExtra(Constants.ELIGIBILITY_RESPONSE, eligibilityResponse)
        }
        requireActivity().setResult(Activity.RESULT_OK, returnIntent)
        requireActivity().finish()
    }

    companion object {
        fun newInstance(
            locationDetails: LocationDetails? = null,
            flowName: String = "",
            poid: String? = null,
            eligibilityResponse: FiberEligibilityResponse? = null
        ): SelectLocationFragment {
            return SelectLocationFragment().apply {
                arguments = bundleOf(
                    Constants.LOCATION_DETAILS_PARAM to locationDetails,
                    Constants.FLOW_NAME_PARAM to flowName,
                    Constants.POID_PARAM to poid,
                    Constants.ELIGIBILITY_RESPONSE to eligibilityResponse
                )
            }
        }

        private fun spaceView(context: android.content.Context): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (12 * resources.displayMetrics.density).toInt()
                )
            }
        }
    }
}
