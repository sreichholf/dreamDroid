package net.reichholf.dreamdroid.ui.drawer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
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
    fun showsSectionsAndPinnedSettings() {
        val state = DrawerListState()
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(state = state, onItemClick = {})
            }
        }

        composeRule.onNodeWithText("Power Control").assertIsDisplayed()
        composeRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Send Message").assertIsDisplayed()
        composeRule.onNodeWithText("Control").assertIsDisplayed()
        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("TV & Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Signal Meter").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Settings & About").assertDoesNotExist()
        composeRule.onNodeWithText("About").assertDoesNotExist()
        composeRule.onNodeWithText("Backup").assertDoesNotExist()
        composeRule.onNodeWithText("Changelog").assertDoesNotExist()
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
    fun boxActionClickDoesNotStaySelected() {
        val state = DrawerListState()
        var clicked = 0
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(
                    state = state,
                    onItemClick = { id ->
                        clicked = id
                    },
                )
            }
        }

        composeRule.onNodeWithText("Power Control").performClick()
        composeRule.waitForIdle()
        assertEquals(R.id.menu_navigation_power, clicked)
        composeRule.runOnIdle {
            assertEquals(R.id.menu_none, state.selectedItemId)
        }
        composeRule.onNodeWithText("TV & Movies").assertIsNotSelected()
    }

    @Test
    fun settingsRowSelects() {
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

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        assertEquals(R.id.menu_navigation_settings, clicked)
        composeRule.onNodeWithText("Settings").assertIsSelected()
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
        composeRule.runOnIdle {
            assertEquals(R.id.menu_none, state.selectedItemId)
        }
    }
}
