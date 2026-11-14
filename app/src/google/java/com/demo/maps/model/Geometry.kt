package com.demo.maps.model

/**
 * Import compatibility only: no new coordinate classes, normalization, equality, bounds algorithm,
 * or serialization are introduced on the Google path. Kotlin compiles each alias to its SDK type.
 * The Huawei source set supplies compatible declarations with these same package/type names.
 */
typealias LatLng = com.google.android.gms.maps.model.LatLng
typealias LatLngBounds = com.google.android.gms.maps.model.LatLngBounds
typealias CameraPosition = com.google.android.gms.maps.model.CameraPosition
