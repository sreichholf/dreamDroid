package net.reichholf.dreamdroid.appwidget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
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
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun styleToggleAndProfileClick() {
        val first =
            ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = false)
        val second =
            ProfileListItem(id = 2, name = "Bedroom", host = "192.168.1.50", active = false)
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
                    }
                )
            }
        }

        composeRule.onNodeWithText("QuickZap Layout (Simple)").assertIsDisplayed()
        composeRule.onNodeWithText("Standard Layout (Full)").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
        assertTrue(lastFullAtClick == true)
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG).assertCountEquals(4)
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)[0]
            .assertLeftPositionInRootIsEqualTo(8.dp)

        composeRule.onNodeWithText("QuickZap Layout (Simple)").performClick()
        composeRule.onNodeWithText("Living Room").performClick()
        assertEquals(first, clicked)
        assertFalse(lastFullAtClick == true)
    }
}
