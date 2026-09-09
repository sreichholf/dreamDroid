package net.reichholf.dreamdroid.ui.drawer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DrawerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsSectionsAndItems() {
        val state = DrawerListState()
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(state = state, onItemClick = {})
            }
        }

        composeRule.onNodeWithText("Control").assertIsDisplayed()
        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("Settings & About").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TV & Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Signal Meter").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickInvokesCallbackAndSelectionHighlightsDestination() {
        val state = DrawerListState()
        var clicked = 0
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(
                    state = state,
                    onItemClick = { id ->
                        clicked = id
                        state.select(id)
                    },
                )
            }
        }

        composeRule.onNodeWithText("Zap").performClick()
        composeRule.waitForIdle()
        assertEquals(R.id.menu_navigation_zap, clicked)
        composeRule.onNodeWithText("Zap").assertIsSelected()
    }

    @Test
    fun clearSelectionRemovesHighlight() {
        val state = DrawerListState()
        state.select(R.id.menu_navigation_services)
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(state = state, onItemClick = {})
            }
        }
        composeRule.onNodeWithText("TV & Movies").assertIsSelected()

        composeRule.runOnIdle { state.clearSelection() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("TV & Movies").assertIsDisplayed()
        // After clear, no drawer destination should stay selected.
        composeRule.runOnIdle {
            assertEquals(R.id.menu_none, state.selectedItemId)
        }
    }
}
