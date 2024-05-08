package com.demo.hmscomposeapp.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import com.huawei.hms.maps.MapView

@Composable
fun rememberMapLifecycle(
    mapView: MapView
): LifecycleObserver {
    return remember {
        LifecycleEventObserver { source, event ->

            mapView.apply {
                when (event) {
                    Lifecycle.Event.ON_CREATE -> onCreate(Bundle())
                    Lifecycle.Event.ON_START -> onStart()
                    Lifecycle.Event.ON_RESUME -> onResume()
                    Lifecycle.Event.ON_PAUSE -> onPause()
                    Lifecycle.Event.ON_STOP -> onStop()
                    Lifecycle.Event.ON_DESTROY -> onDestroy()
                    Lifecycle.Event.ON_ANY -> throw IllegalStateException()
                }
            }
        }
    }

}