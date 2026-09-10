package net.reichholf.dreamdroid.tv.view

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TextCardContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun titleAndContentVisible() {
        composeRule.setContent {
            DreamDroidTheme {
                TextCardContent(
                    title = "Tagesschau",
                    content = "Evening news summary",
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Evening news summary").assertIsDisplayed()
    }
}
