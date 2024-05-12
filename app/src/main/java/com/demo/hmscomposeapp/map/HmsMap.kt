package com.demo.hmscomposeapp.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapView
import com.huawei.hms.maps.model.LatLng


@Composable
fun HmsMap(
    initialLocation: LatLng = LatLng(0.0, 0.0),
    modifier: Modifier = Modifier,
    //onMapReady: (HuaweiMap: HuaweiMap) -> Unit
) {

    val context = LocalContext.current

    val mapView = remember {
        MapView(context)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    lifecycle.addObserver(rememberMapLifecycle(mapView))

    AndroidView(
        factory = {
            mapView.apply {
                mapView.getMapAsync { hMap -> hMap }
            }
        },
        modifier = modifier
    )

}
