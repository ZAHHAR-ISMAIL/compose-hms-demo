package com.demo.hmscomposeapp.customerdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.demo.hmscomposeapp.locationpicker.FiberEligibilityResponse
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.demo.hmscomposeapp.locationpicker.LocationDetails
import com.google.android.gms.maps.model.LatLng as GoogleLatLng

data class CustomerDetailsUiState(
    val locationDetails: LocationDetails? = null,
    val eligibilityResponse: FiberEligibilityResponse? = null
) {
    val latLng: GoogleLatLng?
        get() = locationDetails?.latLng?.toGoogleLatLng()

    val latLngHms: LatLongitude?
        get() = locationDetails?.latLng
}

@Composable
fun CustomerDetails(
    state: CustomerDetailsUiState,
    onMapClick: (GoogleLatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val location = state.locationDetails
    val locationMeta = listOfNotNull(
        location?.city,
        location?.region,
        location?.country
    ).joinToString(separator = ", ")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Customer details",
            style = MaterialTheme.typography.h5
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Service location",
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = location?.address ?: "No location selected yet.",
                    style = MaterialTheme.typography.body1
                )
                if (locationMeta.isNotBlank()) {
                    Text(
                        text = locationMeta,
                        style = MaterialTheme.typography.body2
                    )
                }
                state.eligibilityResponse?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            CommonMapView(
                latLng = state.latLng,
                latLngHMS = state.latLngHms,
                onMapClick = onMapClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = "Tap the map preview to choose or update the customer location.",
            style = MaterialTheme.typography.body2
        )
    }
}
