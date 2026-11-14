package com.demo.maps

import android.os.Parcel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.demo.maps.compose.CameraPositionState
import com.demo.maps.compose.GoogleMap
import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.Marker
import com.demo.maps.compose.MarkerState
import com.demo.maps.compose.rememberCameraPositionState
import com.demo.maps.compose.rememberMarkerState
import com.demo.maps.compose.rememberUpdatedMarkerState
import com.demo.maps.model.BitmapDescriptorFactory
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import com.demo.maps.model.MapStyleOptions
import com.google.android.gms.maps.GoogleMap as NativeGoogleMap
import com.google.android.gms.maps.model.CameraPosition as NativeCameraPosition
import com.google.android.gms.maps.model.LatLng as NativeLatLng
import com.google.android.gms.maps.model.Marker as NativeMarker
import com.google.maps.android.compose.CameraPositionState as NativeCameraState
import com.google.maps.android.compose.MarkerState as NativeMarkerState
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Google-only integration contract: changed imports must still expose Google's real objects. */
class NativeGoogleInteropTest {
    @get:Rule val compose = createComposeRule()
    private val origin = LatLng(24.713630, 46.675293)
    private val destination = LatLng(24.710543, 46.676544)

    @Before fun initializeNativeSdk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
    }

    @Test fun modelsFactoriesAndSaversRetainNativeIdentity() {
        // These assignments intentionally require native types: a custom replacement fails to compile.
        val nativeCoordinate: NativeLatLng = origin
        val nativeBounds: com.google.android.gms.maps.model.LatLngBounds =
            LatLngBounds.builder().include(origin).include(destination).build()
        val position: NativeCameraPosition = CameraPosition.fromLatLngZoom(origin, 15f)
        val update: com.google.android.gms.maps.CameraUpdate = CameraUpdateFactory.newLatLngBounds(nativeBounds, 24)
        val icon: com.google.android.gms.maps.model.BitmapDescriptor = BitmapDescriptorFactory.defaultMarker()
        val properties: com.google.maps.android.compose.MapProperties =
            MapProperties(mapStyleOptions = MapStyleOptions("[]"))
        val settings: com.google.maps.android.compose.MapUiSettings = MapUiSettings()
        assertSame(origin, nativeCoordinate)
        assertNotNull(update)
        assertNotNull(icon)
        assertEquals(com.google.maps.android.compose.MapType.NORMAL, properties.mapType)
        assertTrue(settings.zoomGesturesEnabled)
        assertSame(NativeCameraState.Saver, CameraPositionState.Saver)
        assertSame(NativeMarkerState.Saver, MarkerState.Saver)

        // The original Google Parcelable representation still works after changing the import.
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(position, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            val restored = parcel.readParcelable<NativeCameraPosition>(NativeCameraPosition::class.java.classLoader)
            assertEquals(position, restored)
        } finally {
            parcel.recycle()
        }
    }

    @OptIn(MapsComposeExperimentalApi::class)
    @Test fun forwardedMapSharesOriginalStateAndReturnsNativeMarkerHandles() {
        lateinit var camera: CameraPositionState
        lateinit var marker: MarkerState
        lateinit var scope: CoroutineScope
        val map = AtomicReference<NativeGoogleMap?>(null)
        val clicked = AtomicReference<NativeMarker?>(null)
        val loaded = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        var markerPosition by mutableStateOf(origin)
        compose.setContent {
            camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(origin, 16f) }
            marker = rememberUpdatedMarkerState(markerPosition)
            scope = rememberCoroutineScope()
            GoogleMap(Modifier.fillMaxSize().testTag("interop-map"), cameraPositionState = camera,
                onMapLoaded = { loaded.set(true) }) {
                // A native escape hatch observes the same map; it does not power the bridge.
                MapEffect(Unit) { map.set(it) }
                Marker(state = marker, anchor = Offset(0.5f, 0.5f), onClick = { handle ->
                    val nativeHandle: NativeMarker = handle
                    clicked.set(nativeHandle)
                    true
                })
            }
        }
        compose.waitUntil(30_000) { loaded.get() && map.get() != null }
        val nativeCamera: NativeCameraState = camera
        val nativeMarker: NativeMarkerState = marker
        assertSame(camera, nativeCamera)
        assertSame(marker, nativeMarker)
        compose.onNodeWithTag("interop-map").performTouchInput { click(center) }
        compose.waitUntil(5_000) { clicked.get() != null }
        compose.runOnIdle {
            // An operation through the native state is observed through the compatibility import.
            markerPosition = destination
            scope.launch {
                nativeCamera.animate(CameraUpdateFactory.newLatLngZoom(destination, 17f), 250)
                completed.set(true)
            }
        }
        compose.waitUntil(10_000) { completed.get() }
        compose.runOnIdle {
            assertEquals(destination.latitude, camera.position.target.latitude, 0.0001)
            assertEquals(camera.position, map.get()!!.cameraPosition)
            assertEquals(destination, marker.position)
            assertEquals(destination, clicked.get()!!.position)
        }
    }

    @Test fun forwardedRememberFactoriesRestoreNativeSavedState() {
        val restoration = StateRestorationTester(compose)
        lateinit var camera: CameraPositionState
        lateinit var marker: MarkerState
        restoration.setContent {
            camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(origin, 15f) }
            marker = rememberMarkerState(position = origin)
        }
        lateinit var previousCamera: NativeCameraState
        lateinit var previousMarker: NativeMarkerState
        compose.runOnIdle {
            camera.position = CameraPosition(destination, 17f, 30f, 90f)
            marker.position = destination
            previousCamera = camera
            previousMarker = marker
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertNotSame(previousCamera, camera)
            assertNotSame(previousMarker, marker)
            assertEquals(previousCamera.position, camera.position)
            assertEquals(destination, marker.position)
            assertEquals(NativeCameraState::class.java, camera.javaClass)
            assertEquals(NativeMarkerState::class.java, marker.javaClass)
        }
    }
}
