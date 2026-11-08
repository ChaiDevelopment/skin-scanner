package com.chai.aiskin

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator smoke test: the app launches without crashing and shows the brand splash.
 * Run with: ./gradlew connectedDebugAndroidTest (requires a running emulator or device).
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsBrand() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTextSafe("AI Skin Scanner") }
        compose.onNodeWithText("AI Skin Scanner").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextSafe(text: String) =
        runCatching { onNodeWithText(text).assertExists(); true }.getOrDefault(false)
}
