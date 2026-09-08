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

class TimerListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsTimerNameAndService() {
        composeRule.setContent {
            DreamDroidTheme {
                TimerListScreen(
                    items = listOf(
                        TimerListItem(
                            index = 0,
                            name = "Evening news",
                            serviceName = "ARD",
                            begin = "20:00",
                            end = "20:15",
                            action = "Record",
                            state = "Waiting",
                            stateColor = 0xFF888888.toInt(),
                        ),
                    ),
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Evening news").assertIsDisplayed()
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 – 20:15").assertIsDisplayed()
    }
}
