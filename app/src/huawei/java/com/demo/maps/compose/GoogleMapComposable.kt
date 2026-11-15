package com.demo.maps.compose

import androidx.compose.runtime.ComposableTargetMarker

/** HMS overlays run in the map applier; marker bitmap UI still uses the ordinary UI target. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@ComposableTargetMarker(description = "HMS Map Composable")
annotation class GoogleMapComposable
