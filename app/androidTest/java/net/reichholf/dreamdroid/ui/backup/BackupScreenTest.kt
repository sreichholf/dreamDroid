package net.reichholf.dreamdroid.ui.backup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun keyLabelsAndButtonsVisible() {
        val state = BackupUiState(
            profiles = listOf(
                BackupProfileToggle(id = 1, name = "Home", host = "192.168.1.1", current = true),
                BackupProfileToggle(id = 2, name = "Cellar", host = "192.168.1.2")
            )
        )
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = {},
                    onExport = {},
                    onProfileCheckedChange = { _, _ -> },
                    onExportSettingsChange = {},
                    onIncludePasswordsChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsDisplayed()
        composeRule.onNodeWithText("Cellar (192.168.1.2)").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Export settings").assertIsDisplayed()
        composeRule.onNodeWithText("Include receiver passwords").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsOn()
        composeRule.onNodeWithText("Export settings").assertIsOff()
        composeRule.onNodeWithText("Include receiver passwords").assertIsOn()
        composeRule.onAllNodesWithTag(LIST_ROW_TAG).assertCountEquals(4)
        composeRule.onAllNodesWithTag(LIST_ROW_TAG)[0]
            .assertLeftPositionInRootIsEqualTo(8.dp)
        val exportSettings = composeRule.onNode(hasText("Export settings") and isToggleable())
            .getBoundsInRoot()
        val exportHeight = exportSettings.bottom - exportSettings.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$exportHeight",
            exportHeight >= 56.dp
        )
    }

    @Test
    fun switchesAndButtonsReportToTheCaller() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = BackupUiState(
                        profiles = listOf(BackupProfileToggle(id = 7, name = "Home", host = "h"))
                    ),
                    onImport = { events += "import" },
                    onExport = { events += "export" },
                    onProfileCheckedChange = { id, checked -> events += "profile $id $checked" },
                    onExportSettingsChange = { events += "settings $it" },
                    onIncludePasswordsChange = { events += "passwords $it" }
                )
            }
        }

        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText("Home (h)").performClick()
        composeRule.onNodeWithText("Export settings").performClick()
        composeRule.onNodeWithText("Include receiver passwords").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("import", "export", "profile 7 false", "settings true", "passwords false"),
            events
        )
    }
}
