package com.demo.hmscomposeapp.customerdetails

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.demo.hmscomposeapp.locationpicker.Constants
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.huawei.hms.maps.CameraUpdateFactory
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapView
import com.huawei.hms.maps.model.Marker
import com.huawei.hms.maps.model.MarkerOptions
import com.huawei.hms.maps.model.LatLng as HuaweiLatLng

@Composable
fun CommonMapView(
    latLng: GoogleLatLng?,
    latLngHMS: LatLongitude?,
    onMapClick: (GoogleLatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveLatLng = latLngHMS ?: latLng?.let {
        LatLongitude(
            latitude = it.latitude,
            longitude = it.longitude
        )
    }

    HuaweiMapCompose(
        latLng = effectiveLatLng,
        onMapClick = { hmsLatLng ->
            onMapClick(
                GoogleLatLng(
                    hmsLatLng.latitude,
                    hmsLatLng.longitude
                )
            )
        },
        modifier = modifier
    )
}

@Composable
private fun HuaweiMapCompose(
    latLng: LatLongitude?,
    onMapClick: (HuaweiLatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnMapClick by rememberUpdatedState(onMapClick)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    var huaweiMap by remember { mutableStateOf<HuaweiMap?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    huaweiMap = map
                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    map.setOnMapClickListener { selected ->
                        latestOnMapClick(selected)
                    }
                }
            }
        }
    )

    LaunchedEffect(huaweiMap, latLng?.latitude, latLng?.longitude) {
        val map = huaweiMap ?: return@LaunchedEffect
        val target = latLng?.toHuaweiLatLng()
            ?: HuaweiLatLng(Constants.DEFAULT_LATITUDE, Constants.DEFAULT_LONGITUDE)

        if (latLng == null) {
            marker?.remove()
            marker = null
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 10f))
            return@LaunchedEffect
        }

        if (marker == null) {
            marker = map.addMarker(MarkerOptions().position(target))
        } else {
            marker?.position = target
        }

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
    }
}
