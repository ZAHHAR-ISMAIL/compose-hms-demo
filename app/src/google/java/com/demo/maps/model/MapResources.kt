package com.demo.maps.model

/**
 * Styles and icons retain their Google SDK identity and native initialization requirements.
 * Resources still resolve per flavor because Google and HMS style JSON use different formats.
 */
typealias MapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions
typealias BitmapDescriptor = com.google.android.gms.maps.model.BitmapDescriptor
typealias BitmapDescriptorFactory = com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Google callbacks expose their original SDK handles, without allocating handle adapters.
 * These aliases are Kotlin source conveniences; they do not create com.demo.maps JVM classes
 * for Java, reflection, or precompiled libraries. Huawei's handles expose a supported subset.
 */
typealias Marker = com.google.android.gms.maps.model.Marker
typealias Polygon = com.google.android.gms.maps.model.Polygon
typealias Polyline = com.google.android.gms.maps.model.Polyline
typealias Circle = com.google.android.gms.maps.model.Circle
