package com.demo.hmscomposeapp

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.demo.hmscomposeapp.locationpicker.LatLongitude
import com.demo.hmscomposeapp.map.LocationProvider
import com.demo.hmscomposeapp.map.MapProvider
import org.junit.Rule
import org.junit.Test

class MapDemoTest {
    @get:Rule val compose = createComposeRule()
    private val tapped = LatLongitude(24.72, 46.68)
    private val map = object : MapProvider {
        @Composable
        override fun Map(marker: LatLongitude, route: List<LatLongitude>, modifier: Modifier,
                         onMapTap: (LatLongitude) -> Unit) {
            Column(modifier) {
                Text("Marker: ${marker.latitude}")
                Text("Route points: ${route.size}")
                Button(onClick = { onMapTap(tapped) }) { Text("Tap map") }
            }
        }
    }
    private val location = object : LocationProvider {
        override suspend fun currentLocation() = tapped
    }

    @Test fun locationTimeoutKeepsManualSelectionAvailable() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val timeoutLocation = object : LocationProvider {
            override suspend fun currentLocation(): LatLongitude = kotlinx.coroutines.withTimeout(1) {
                kotlinx.coroutines.awaitCancellation()
            }
        }
        compose.setContent { MapDemo(map, timeoutLocation) }
        compose.onNodeWithText("Pick location").performClick()
        compose.onNodeWithText("Use current location").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Location timed out. Try again or tap the map.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tap map").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Marker: 24.72").assertExists()
    }

    @Test fun confirmThenCancelPreservesConfirmedSelectionAndRoute() {
        compose.setContent { MapDemo(map, location) }
        compose.onNodeWithText("Route points: 17").assertExists()
        compose.onNodeWithText("Pick location").performClick()
        compose.onNodeWithText("Route points: 0").assertExists()
        compose.onNodeWithText("Tap map").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Marker: 24.72").assertExists()
        compose.onNodeWithText("Pick location").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Marker: 24.72").assertExists()
        compose.onNodeWithText("Route points: 17").assertExists()
    }

    @Test fun cancelledDraftDoesNotChangeLauncher() {
        compose.setContent { MapDemo(map, location) }
        compose.onNodeWithText("Pick location").performClick()
        compose.onNodeWithText("Tap map").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Marker: 24.7136").assertExists()
    }

    @Test fun pickerAndDraftSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MapDemo(map, location) }
        compose.onNodeWithText("Pick location").performClick()
        compose.onNodeWithText("Tap map").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Select location").assertExists()
        compose.onNodeWithText("Marker: 24.72").assertExists()
        compose.onNodeWithText("Confirm").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Marker: 24.72").assertExists()
    }
}
