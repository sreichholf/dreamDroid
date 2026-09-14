package net.reichholf.dreamdroid.ui.remote

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
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
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun fullRemoteShowsCoreKeys() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    layout = VirtualRemoteLayout.Full,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> }
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
                    onKey = { _, _ -> }
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
                    onKey = { _, _ -> }
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
                    modifier = Modifier.size(720.dp, 800.dp),
                    layout = VirtualRemoteLayout.Full,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> }
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
            kotlin.math.abs((okCenterX - rootCenterX).value) < 24f
        )
    }

    @Test
    fun fullRemoteAndToggleFitShortPhoneViewport() {
        composeRule.setContent {
            DreamDroidTheme {
                VirtualRemoteScreen(
                    modifier = Modifier.size(360.dp, 520.dp),
                    layout = VirtualRemoteLayout.Full,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                    onToggleLayout = {},
                    toggleIconRes = R.drawable.ic_action_list,
                    toggleContentDescription = "QuickZap Layout (Simple)"
                )
            }
        }
        val root = composeRule.onRoot().getBoundsInRoot()
        val tv = composeRule.onNodeWithText("TV").getBoundsInRoot()
        val rec = composeRule.onNodeWithText("REC").getBoundsInRoot()
        val toggle = composeRule.onNodeWithTag(VIRTUAL_REMOTE_LAYOUT_TOGGLE_TAG).getBoundsInRoot()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("REC").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("QuickZap Layout (Simple)").assertIsDisplayed()
        assertTrue("TV bottom=${tv.bottom} root=${root.bottom}", tv.bottom <= root.bottom)
        assertTrue("REC bottom=${rec.bottom} root=${root.bottom}", rec.bottom <= root.bottom)
        assertTrue(
            "toggle bottom=${toggle.bottom} root=${root.bottom}",
            toggle.bottom <= root.bottom
        )
        assertTrue("toggle top=${toggle.top} must be on-screen", toggle.top >= root.top)
    }

    @Test
    fun layoutToggleSwitchesFullPadToQuickZap() {
        composeRule.setContent {
            DreamDroidTheme {
                var layout by remember { mutableStateOf(VirtualRemoteLayout.Full) }
                VirtualRemoteScreen(
                    layout = layout,
                    playButtonAsPlayPause = false,
                    onKey = { _, _ -> },
                    onToggleLayout = {
                        layout = if (layout == VirtualRemoteLayout.Full) {
                            VirtualRemoteLayout.QuickZap
                        } else {
                            VirtualRemoteLayout.Full
                        }
                    },
                    toggleIconRes = R.drawable.ic_action_list,
                    toggleContentDescription = "QuickZap Layout (Simple)"
                )
            }
        }
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithTag(VIRTUAL_REMOTE_LAYOUT_TOGGLE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1").assertDoesNotExist()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("V+").assertIsDisplayed()
    }
}
