package net.reichholf.dreamdroid.ui.pick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.zap.ZapListMapper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimerServicePickScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun serviceRowsClickAndMapToHashEdge() {
        val channel = Service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD")
        var clicked: Service? = null
        composeRule.setContent {
            DreamDroidTheme {
                PickServiceScreen(
                    items = listOf(channel),
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed().performClick()
        assertEquals(channel, clicked)
        val map = ZapListMapper.toBouquetMap(clicked)
        assertEquals("Das Erste HD", map.getString(ServiceKeys.KEY_NAME))
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", map.getString(ServiceKeys.KEY_REFERENCE))
    }
}
