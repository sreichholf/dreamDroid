package net.reichholf.dreamdroid.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun keyLabelsAndButtonsVisible() {
        val state = BackupUiState().apply {
            setProfiles(
                listOf(
                    BackupProfileToggle(
                        id = 1,
                        label = "Home (192.168.1.1) (current)",
                        checked = true,
                    ),
                ),
            )
        }
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Export settings").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsOn()
        composeRule.onNodeWithText("Export settings").assertIsOff()
    }
}
