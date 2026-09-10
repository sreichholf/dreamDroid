package net.reichholf.dreamdroid.appwidget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VirtualRemoteWidgetConfigScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun styleToggleAndProfileClick() {
        val first = ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = false)
        val second = ProfileListItem(id = 2, name = "Bedroom", host = "192.168.1.50", active = false)
        var lastFullAtClick: Boolean? = null
        var clicked: ProfileListItem? = null

        composeRule.setContent {
            DreamDroidTheme {
                var isFull by remember { mutableStateOf(false) }
                VirtualRemoteWidgetConfigScreen(
                    profiles = listOf(first, second),
                    isFull = isFull,
                    onStyleFullChange = { isFull = it },
                    onProfileClick = { profile ->
                        lastFullAtClick = isFull
                        clicked = profile
                    },
                )
            }
        }

        composeRule.onNodeWithText("QuickZap Layout (Simple)").assertIsDisplayed()
        composeRule.onNodeWithText("Standard Layout (Full)").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
        assertTrue(lastFullAtClick == true)

        composeRule.onNodeWithText("QuickZap Layout (Simple)").performClick()
        composeRule.onNodeWithText("Living Room").performClick()
        assertEquals(first, clicked)
        assertFalse(lastFullAtClick == true)
    }
}
