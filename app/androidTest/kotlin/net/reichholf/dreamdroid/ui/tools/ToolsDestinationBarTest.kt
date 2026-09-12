package net.reichholf.dreamdroid.ui.tools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ToolsDestinationBarTest {
	@get:Rule
	val composeRule = createComposeRule()

	@Before
	fun forceAlwaysNight() {
		PreferenceManager.getDefaultSharedPreferences(
			InstrumentationRegistry.getInstrumentation().targetContext,
		).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
	}

	@Test
	fun showsToolDestinations() {
		composeRule.setContent {
			DreamDroidTheme {
				ToolsDestinationBar(
					selected = ToolsDestination.SCREENSHOT,
					onDestinationSelected = {},
				)
			}
		}
		composeRule.onNodeWithText("Screenshot").assertIsDisplayed()
		composeRule.onNodeWithText("Device Information").assertIsDisplayed()
		composeRule.onNodeWithText("Signal Meter").assertIsDisplayed()
		composeRule.onNodeWithText("Screenshot").assertIsSelected()
	}

	@Test
	fun selectingDestinationReportsChoice() {
		val selected = mutableListOf<ToolsDestination>()
		composeRule.setContent {
			DreamDroidTheme {
				ToolsDestinationBar(
					selected = ToolsDestination.SCREENSHOT,
					onDestinationSelected = { selected += it },
				)
			}
		}
		composeRule.onNodeWithText("Signal Meter").performClick()
		assertEquals(listOf(ToolsDestination.SIGNAL), selected)
	}
}
