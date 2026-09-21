package net.reichholf.dreamdroid.ui.session

import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.ExplainAlertDialog
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerScreen
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotScreen
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotUiState
import net.reichholf.dreamdroid.ui.signal.SignalScreen
import net.reichholf.dreamdroid.ui.signal.SignalUiState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnlineOnlyExplainTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun greyedBoxActionClickStillWorksAndExplains() {
        var clicked = 0
        composeRule.setContent {
            DreamDroidTheme {
                var showExplain by remember { mutableStateOf(false) }
                DrawerScreen(
                    state = DrawerListState(),
                    boxActionsBlocked = true,
                    onItemClick = { id ->
                        clicked = id
                        showExplain = true
                    }
                )
                if (showExplain) {
                    ExplainAlertDialog(
                        title = "Needs the receiver",
                        message = "Connect to the receiver to use this.",
                        onDismiss = { showExplain = false }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Power Control").performClick()
        composeRule.waitForIdle()
        assertEquals(R.id.menu_navigation_power, clicked)
        composeRule.onNodeWithText("Needs the receiver").assertIsDisplayed()
        composeRule.onNodeWithText("Connect to the receiver to use this.").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Needs the receiver").assertDoesNotExist()
    }

    @Test
    fun greyedToolsScreenshotReloadStillExplains() {
        composeRule.setContent {
            DreamDroidTheme {
                var showExplain by remember { mutableStateOf(false) }
                ScreenshotScreen(
                    state = ScreenshotUiState().apply { actionsEnabled = true },
                    grabBlocked = true,
                    onReload = { showExplain = true },
                    onShare = {},
                    onSave = {}
                )
                if (showExplain) {
                    ExplainAlertDialog(
                        title = "Needs the receiver",
                        message = "Connect to the receiver to use this.",
                        onDismiss = { showExplain = false }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Reload").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Needs the receiver").assertIsDisplayed()
    }

    @Test
    fun greyedToolsSignalEnableStillExplains() {
        var enableClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                var showExplain by remember { mutableStateOf(false) }
                SignalScreen(
                    state = SignalUiState(),
                    meterBlocked = true,
                    onEnabledChange = {
                        enableClicks += 1
                        showExplain = true
                    },
                    onAcousticChange = {}
                )
                if (showExplain) {
                    ExplainAlertDialog(
                        title = "Needs the receiver",
                        message = "Connect to the receiver to use this.",
                        onDismiss = { showExplain = false }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Enable").performClick()
        composeRule.waitForIdle()
        assertEquals(1, enableClicks)
        composeRule.onNodeWithText("Needs the receiver").assertIsDisplayed()
    }

    @Test
    fun germanNeedsReceiverLongUsesNaturalCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.GERMAN)
        val de = context.createConfigurationContext(config)
        assertEquals(
            "Diese Funktion benötigt eine Verbindung zur Box",
            de.getString(R.string.session_needs_receiver_long)
        )
    }
}
