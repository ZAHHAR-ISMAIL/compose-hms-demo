package com.demo.hmscomposeapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Exercises the complete complex screen and real Activity/native MapView recreation. */
class LiveRideLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun complexMapRestoresScreenControlsAndNativeMapAfterRecreation() {
        // Freeze the continuous demo animation while operating controls deterministically.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Live ride demo").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Pause").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        waitForMap()
        compose.onNodeWithText("Night style").performClick()
        compose.onNodeWithText("Fit route").performClick()
        compose.onNodeWithText("Day style").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Resume").assertExists()
        compose.onNodeWithText("Day style").assertExists()
        waitForMap()
        compose.onNodeWithText("Day style").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Pick location").assertExists()
    }

    private fun waitForMap() = compose.waitUntil(30_000) {
        compose.onAllNodesWithText("Map ready").fetchSemanticsNodes().isNotEmpty()
    }
}
