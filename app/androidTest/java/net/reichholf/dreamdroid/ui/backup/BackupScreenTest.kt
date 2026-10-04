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
import androidx.compose.ui.test.onAllNodesWithText
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
    fun importCardProfilesAndOptionsAreShown() {
        setScreen(
            BackupUiState(
                profiles = listOf(
                    BackupProfileToggle(
                        id = 1,
                        name = "Home",
                        host = "192.168.1.1",
                        current = true
                    ),
                    BackupProfileToggle(id = 2, name = "Cellar", host = "192.168.1.2")
                )
            )
        )

        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Choose file").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Select none").assertIsDisplayed()
        composeRule.onNode(hasText("Home") and hasText("192.168.1.1") and hasText("Current"))
            .assertIsOn()
        composeRule.onNode(hasText("Cellar") and hasText("192.168.1.2")).assertIsOn()
        composeRule.onAllNodesWithText("Current").assertCountEquals(1)
        composeRule.onNodeWithText("Receiver passwords").assertIsOff()
        composeRule.onNodeWithText(
            "Left out. On import, existing profiles keep their saved passwords."
        )
            .assertIsDisplayed()
        composeRule.onNodeWithText("App settings").assertIsOff()
        composeRule.onAllNodesWithTag(LIST_ROW_TAG).assertCountEquals(4)
        composeRule.onAllNodesWithTag(LIST_ROW_TAG)[0]
            .assertLeftPositionInRootIsEqualTo(8.dp)
        val settings = composeRule.onNode(hasText("App settings") and isToggleable())
            .getBoundsInRoot()
        val height = settings.bottom - settings.top
        assertTrue("Switch rows are at least 56.dp, height=$height", height >= 56.dp)
    }

    @Test
    fun partialSelectionOffersSelectAllAndPasswordsWarnWhenOn() {
        setScreen(
            BackupUiState(
                profiles = listOf(
                    BackupProfileToggle(id = 1, name = "Home", host = "h1"),
                    BackupProfileToggle(id = 2, name = "Cellar", host = "h2", checked = false)
                ),
                includePasswords = true
            )
        )

        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Select all").assertIsDisplayed()
        composeRule.onNode(hasText("Cellar") and isToggleable()).assertIsOff()
        composeRule.onNodeWithText("Receiver passwords").assertIsOn()
        composeRule.onNodeWithText("In the file. Anyone who has it can sign in to your receivers.")
            .assertIsDisplayed()
    }

    @Test
    fun rowsAndButtonsReportToTheCaller() {
        val events = mutableListOf<String>()
        setScreen(
            BackupUiState(
                profiles = listOf(BackupProfileToggle(id = 7, name = "Home", host = "h"))
            ),
            events
        )

        composeRule.onNodeWithText("Choose file").performClick()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Select none").performClick()
        composeRule.onNodeWithText("App settings").performClick()
        composeRule.onNodeWithText("Receiver passwords").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("import", "profile 7 false", "all false", "settings true", "passwords true"),
            events
        )
    }

    private fun setScreen(state: BackupUiState, events: MutableList<String> = mutableListOf()) {
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = { events += "import" },
                    onProfileCheckedChange = { id, checked -> events += "profile $id $checked" },
                    onAllProfilesCheckedChange = { events += "all $it" },
                    onExportSettingsChange = { events += "settings $it" },
                    onIncludePasswordsChange = { events += "passwords $it" }
                )
            }
        }
    }
}
