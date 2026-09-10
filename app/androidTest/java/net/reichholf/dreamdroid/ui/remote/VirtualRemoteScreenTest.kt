package net.reichholf.dreamdroid.ui.remote

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VirtualRemoteScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun fullRemoteShowsCoreKeys() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    layout = VirtualRemoteLayout.Full,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("PWR").assertIsDisplayed()
        composeRule.onNodeWithText("Mute").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Up").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
    }

    @Test
    fun simpleRemoteOmitsTransportPlay() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    layout = VirtualRemoteLayout.Simple,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("RADIO").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun quickZapShowsNavigationWithoutDigits() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    layout = VirtualRemoteLayout.QuickZap,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("V+").assertIsDisplayed()
        composeRule.onNodeWithText("B-").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun fullRemoteIsHorizontallyCenteredInWideHost() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    modifier = Modifier.fillMaxSize(),
                    layout = VirtualRemoteLayout.Full,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                )
            }
        }
        val root = composeRule.onRoot().getBoundsInRoot()
        val ok = composeRule.onNodeWithText("OK").getBoundsInRoot()
        val okCenterX = (ok.left + ok.right) / 2
        val rootCenterX = (root.left + root.right) / 2
        // OK sits in the middle of the 3-wide nav pad, which is centered in the host.
        assertTrue(
            "OK center=$okCenterX root center=$rootCenterX",
            kotlin.math.abs((okCenterX - rootCenterX).value) < 24f,
        )
    }
}
