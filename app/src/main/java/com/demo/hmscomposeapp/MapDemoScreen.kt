package com.demo.hmscomposeapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.demo.hmscomposeapp.map.LocationProvider
import com.demo.hmscomposeapp.map.MapProvider
import com.demo.hmscomposeapp.map.createLocationProvider
import com.demo.hmscomposeapp.map.createMapProvider

@Composable
internal fun MapDemo(
    suppliedMapProvider: MapProvider? = null,
    suppliedLocationProvider: LocationProvider? = null,
) {
    val context = LocalContext.current
    val mapProvider = remember(suppliedMapProvider) {
        suppliedMapProvider ?: createMapProvider()
    }
    val locationProvider = remember(context, suppliedLocationProvider) {
        suppliedLocationProvider ?: createLocationProvider(context.applicationContext)
    }
    val state = rememberMapDemoState(locationProvider)

    Surface(Modifier.fillMaxSize().systemBarsPadding()) {
        if (state.isPicking) LocationPicker(mapProvider, state) else Launcher(mapProvider, state)
    }
}

@Composable
private fun Launcher(mapProvider: MapProvider, state: MapDemoState) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.map_demo_title),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(16.dp),
        )
        mapProvider.Map(
            marker = state.selected,
            route = DemoMapData.route,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onMapTap = {},
        )
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.sample_route_label))
            Text(
                text = stringResource(R.string.route_attribution),
                style = MaterialTheme.typography.caption,
            )
            Text(stringResource(R.string.coordinates, state.selected.latitude, state.selected.longitude))
            Button(onClick = state::openPicker) {
                Text(stringResource(R.string.pick_location))
            }
        }
    }
}

@Composable
private fun LocationPicker(mapProvider: MapProvider, state: MapDemoState) {
    val context = LocalContext.current
    BackHandler(onBack = state::cancelPicker)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) state.useCurrentLocation() else state.permissionDenied()
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.select_location),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(16.dp),
        )
        mapProvider.Map(
            marker = state.draft,
            route = emptyList(),
            modifier = Modifier.fillMaxWidth().weight(1f),
            onMapTap = state::selectOnMap,
        )
        Column(Modifier.padding(16.dp)) {
            Text(
                if (state.isResolvingAddress) stringResource(R.string.resolving_address)
                else state.address.orEmpty(),
            )
            Text(stringResource(R.string.coordinates, state.draft.latitude, state.draft.longitude))
            LocationErrorMessage(state.locationError)
            Button(
                enabled = !state.isLocating,
                onClick = {
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                    if (permissions.any {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                    ) {
                        state.useCurrentLocation()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                },
            ) {
                Text(
                    if (state.isLocating) stringResource(R.string.finding_location)
                    else stringResource(R.string.use_current_location),
                )
            }
            Row {
                TextButton(onClick = state::cancelPicker) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = state::confirmPicker) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun LocationErrorMessage(error: LocationError?) {
    val message = when (error) {
        LocationError.PermissionDenied -> R.string.location_permission_denied
        LocationError.Timeout -> R.string.location_timeout
        LocationError.Unavailable -> R.string.location_unavailable
        null -> return
    }
    Text(stringResource(message), color = MaterialTheme.colors.error)
}
