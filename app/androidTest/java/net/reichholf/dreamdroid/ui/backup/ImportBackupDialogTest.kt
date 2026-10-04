package net.reichholf.dreamdroid.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImportBackupDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun partsMissingFromTheFileAreOffAndDisabled() {
        setDialog(
            review(passwordsAvailable = false, settingsAvailable = false)
        )

        composeRule.onNodeWithText("Import backup").assertIsDisplayed()
        composeRule.onNode(hasText("Kitchen") and hasText("Replaces saved")).assertIsOn()
        composeRule.onNode(hasText("Cellar") and isToggleable()).assertIsOn()
        composeRule.onNode(hasText("Receiver passwords") and isToggleable())
            .assertIsOff()
            .assertIsNotEnabled()
        composeRule.onNode(hasText("App settings") and isToggleable())
            .assertIsOff()
            .assertIsNotEnabled()
        composeRule.onNode(hasText("Import") and hasClickAction()).assertIsEnabled()
    }

    @Test
    fun partsInTheFileCanBeChosen() {
        setDialog(review(passwordsAvailable = true, settingsAvailable = true))

        composeRule.onNode(hasText("Receiver passwords") and isToggleable())
            .assertIsOn()
            .assertIsEnabled()
        composeRule.onNodeWithText("The file's passwords replace the saved ones.")
            .assertIsDisplayed()
        composeRule.onNode(hasText("App settings") and isToggleable())
            .assertIsOn()
            .assertIsEnabled()
    }

    @Test
    fun nothingChosenDisablesImport() {
        setDialog(
            review(passwordsAvailable = true, settingsAvailable = false).let { review ->
                review.copy(profiles = review.profiles.map { it.copy(checked = false) })
            }
        )

        composeRule.onNodeWithText("0 of 2").assertIsDisplayed()
        composeRule.onNode(hasText("Receiver passwords") and isToggleable())
            .assertIsOff()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Profiles already on the device keep their passwords; new ones have none."
        )
            .assertIsDisplayed()
        composeRule.onNode(hasText("Import") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun rowsAndButtonsReportToTheCaller() {
        val events = mutableListOf<String>()
        setDialog(review(passwordsAvailable = true, settingsAvailable = true), events)

        composeRule.onNodeWithText("Cellar").performScrollTo().performClick()
        composeRule.onNodeWithText("Select none").performClick()
        composeRule.onNodeWithText("Receiver passwords").performClick()
        composeRule.onNodeWithText("App settings").performClick()
        composeRule.onNode(hasText("Import") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                "profile 1 false",
                "all false",
                "passwords false",
                "settings false",
                "confirm",
                "dismiss"
            ),
            events
        )
    }

    private fun review(passwordsAvailable: Boolean, settingsAvailable: Boolean) = ImportReview(
        profiles = listOf(
            ImportProfileToggle(index = 0, name = "Kitchen", host = "10.0.0.3", replaces = true),
            ImportProfileToggle(index = 1, name = "Cellar", host = "10.0.0.4")
        ),
        passwordsAvailable = passwordsAvailable,
        includePasswords = passwordsAvailable,
        settingsAvailable = settingsAvailable,
        includeSettings = settingsAvailable
    )

    private fun setDialog(review: ImportReview, events: MutableList<String> = mutableListOf()) {
        composeRule.setContent {
            DreamDroidTheme {
                ImportBackupDialog(
                    review = review,
                    onProfileCheckedChange = { index, checked ->
                        events += "profile $index $checked"
                    },
                    onAllProfilesCheckedChange = { events += "all $it" },
                    onPasswordsChange = { events += "passwords $it" },
                    onSettingsChange = { events += "settings $it" },
                    onDismiss = { events += "dismiss" },
                    onConfirm = { events += "confirm" }
                )
            }
        }
    }
}
