package net.reichholf.dreamdroid.ui.about

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsVersionSubstringAndLicensesButton() {
        composeRule.setContent {
            DreamDroidTheme {
                AboutScreen(content = sampleAboutContent(), onLicensesClick = {})
            }
        }
        composeRule.onNodeWithText("2.0.462", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Licenses").assertIsDisplayed()
    }

    @Test
    fun localContentColorMatchesOnSurfaceInNight() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                AboutScreen(content = sampleAboutContent(), onLicensesClick = {})
            }
        }
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun licensesRowsAreLinksWithWebsiteDescription() {
        composeRule.setContent {
            DreamDroidTheme {
                LicensesScreen(licenses = dreamDroidLicenses())
            }
        }
        composeRule.onNodeWithText("AndroidX").assertIsDisplayed()
        val row = composeRule.onNodeWithContentDescription(
            "AndroidX https://developer.android.com/jetpack/androidx/",
            substring = true
        ).assertIsDisplayed().getBoundsInRoot()
        val height = row.bottom - row.top
        assertTrue("license rows are at least 48.dp, height=$height", height >= 48.dp)
    }
}

internal fun sampleAboutContent() = AboutContent(
    title = "About",
    version = "dreamDroid 2.0.462-debug",
    license = "GPLv3",
    sourceLink = "Source code available at: http://github.com/sreichholf/dreamDroid",
    licensesLabel = "Licenses"
)
