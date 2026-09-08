package net.reichholf.dreamdroid.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsVersionSubstringAndLicensesButton() {
        composeRule.setContent {
            AboutTheme {
                AboutScreen(
                content = AboutContent(
                    title = "About",
                    version = "dreamDroid 1.15.460-debug",
                    license = "GPLv3",
                    sourceLink = "Source code available at: http://github.com/sreichholf/dreamDroid",
                    licensesLabel = "Licenses",
                ),
                onLicensesClick = {},
            )
        }
        composeRule.onNodeWithText("1.15.460", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Licenses").assertIsDisplayed()
    }
}
