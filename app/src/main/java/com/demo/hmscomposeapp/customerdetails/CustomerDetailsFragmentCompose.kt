package com.demo.hmscomposeapp.customerdetails

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.demo.hmscomposeapp.locationpicker.Constants
import com.demo.hmscomposeapp.locationpicker.FiberEligibilityResponse
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.demo.hmscomposeapp.locationpicker.LocationDetails
import com.demo.hmscomposeapp.locationpicker.SelectLocationActivity
import com.demo.hmscomposeapp.locationpicker.parcelable
import com.demo.hmscomposeapp.ui.theme.HmsComposeAppTheme
import com.google.android.gms.maps.model.LatLng as GoogleLatLng

class CustomerDetailsFragmentCompose : Fragment() {

    private var uiState by mutableStateOf(CustomerDetailsUiState())

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val locationDetails = result.data?.parcelable<LocationDetails>(Constants.PLACE_PARAM)
            ?: return@registerForActivityResult
        val eligibilityResponse = result.data?.parcelable<FiberEligibilityResponse>(
            Constants.ELIGIBILITY_RESPONSE
        )

        locationDetails.latLng?.toGoogleLatLng()?.let { latLng ->
            onMapLocationPicked(
                latLng = latLng,
                address = locationDetails.address,
                city = locationDetails.city,
                region = locationDetails.region,
                country = locationDetails.country
            )
        }

        if (eligibilityResponse != null) {
            uiState = uiState.copy(eligibilityResponse = eligibilityResponse)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialLocation = arguments?.parcelable<LocationDetails>(Constants.PLACE_PARAM)
        val eligibilityResponse = arguments?.parcelable<FiberEligibilityResponse>(
            Constants.ELIGIBILITY_RESPONSE
        )
        uiState = CustomerDetailsUiState(
            locationDetails = initialLocation,
            eligibilityResponse = eligibilityResponse
        )
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HmsComposeAppTheme {
                    CustomerDetails(
                        state = uiState,
                        onMapClick = ::openLocationPicker
                    )
                }
            }
        }
    }

    private fun openLocationPicker(clickedLatLng: GoogleLatLng) {
        val selectedLocation = uiState.locationDetails
        val seedLocation = selectedLocation ?: LocationDetails(
            latLng = LatLongitude(
                latitude = clickedLatLng.latitude,
                longitude = clickedLatLng.longitude
            )
        )

        val intent = SelectLocationActivity.callingIntent(
            context = requireContext(),
            locationDetails = seedLocation,
            flowName = "AirFiber",
            poid = null,
            eligibilityResponse = uiState.eligibilityResponse
        )
        locationPickerLauncher.launch(intent)
    }

    private fun onMapLocationPicked(
        latLng: GoogleLatLng,
        address: String?,
        city: String?,
        region: String?,
        country: String?
    ) {
        uiState = uiState.copy(
            locationDetails = LocationDetails(
                latLng = LatLongitude(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                ),
                address = address,
                city = city,
                region = region,
                country = country
            )
        )
    }

    companion object {
        fun newInstance(
            locationDetails: LocationDetails? = null,
            eligibilityResponse: FiberEligibilityResponse? = null
        ): CustomerDetailsFragmentCompose {
            return CustomerDetailsFragmentCompose().apply {
                arguments = bundleOf(
                    Constants.PLACE_PARAM to locationDetails,
                    Constants.ELIGIBILITY_RESPONSE to eligibilityResponse
                )
            }
        }
    }
}
