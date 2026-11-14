package com.demo.maps

/**
 * These names are the actual Google SDK classes in this flavor, not wrapper commands.
 * A command created through this import can also be passed directly to native Google APIs.
 * Initialize the SDK before using CameraUpdateFactory, just as with the original Google import;
 * constructing a command before a MapView exists is different from constructing it before SDK setup.
 */
typealias CameraUpdate = com.google.android.gms.maps.CameraUpdate
typealias CameraUpdateFactory = com.google.android.gms.maps.CameraUpdateFactory
