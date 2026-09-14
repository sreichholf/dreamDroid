package net.reichholf.dreamdroid.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DestinationBarTest {
	@get:Rule
	val composeRule = createComposeRule()

	@Before
	fun forceAlwaysNight() {
		PreferenceManager.getDefaultSharedPreferences(
			InstrumentationRegistry.getInstrumentation().targetContext,
		).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
	}

	@Test
	fun sharedBarReportsSelectionByIndex() {
		val items = listOf(
			DestinationBarItem(R.string.tv, R.drawable.ic_menu_tv),
			DestinationBarItem(R.string.radio, R.drawable.ic_menu_radio),
		)
		val selected = mutableListOf<Int>()
		composeRule.setContent {
			DreamDroidTheme {
				DestinationBar(
					items = items,
					selectedIndex = 0,
					onSelect = { selected += it },
				)
			}
		}
		composeRule.onNodeWithText("TV").assertIsDisplayed()
		composeRule.onNodeWithText("TV").assertIsSelected()
		composeRule.onNodeWithText("Radio").performClick()
		assertEquals(listOf(1), selected)
	}
}
