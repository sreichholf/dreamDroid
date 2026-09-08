package net.reichholf.dreamdroid.ui.services

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

class ServiceListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsChannelAndNowNext() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL,
                            nowTitle = "Tagesschau",
                            nowStart = "20:00",
                            nowDuration = "15",
                            nextTitle = "Wetter",
                            nextStart = "20:15",
                            nextDuration = "5",
                            progressMax = 15,
                            progress = 3,
                        ),
                    ),
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00  Tagesschau  15").assertIsDisplayed()
        composeRule.onNodeWithText("20:15  Wetter  5").assertIsDisplayed()
    }
}
