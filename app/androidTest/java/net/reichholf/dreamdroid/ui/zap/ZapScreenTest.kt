package net.reichholf.dreamdroid.ui.zap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.testutil.COMPACT_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ZapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededServicesShowNames() {
        composeRule.setContent {
            DreamDroidTheme {
                ZapScreen(
                    items = listOf(
                        Service("1:0:1:1:1:1:1:0:0:0:", "ARD HD"),
                        Service("1:0:1:2:1:1:1:0:0:0:", "ZDF HD")
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        composeRule.onNodeWithText("ARD HD").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF HD").assertIsDisplayed()
    }

    @Test
    fun wideWindowsGetFewerLargerCards() {
        showInFixedGrid()
        assertEquals("two cards in a row", 2, columns())

        windowWidth = EXPANDED_WINDOW_WIDTH

        assertEquals("one card per row", 1, columns())
    }

    @Test
    fun aPhoneInLandscapeKeepsTheSmallCards() {
        windowWidth = EXPANDED_WINDOW_WIDTH
        windowHeight = 400.dp
        showInFixedGrid()

        assertEquals(2, columns())
    }

    @Test
    fun aMediumWindowGetsMediumCards() {
        gridWidth = 280.dp
        showInFixedGrid()
        assertEquals("70dp cards", 2, columns())

        windowWidth = 700.dp

        assertEquals("90dp cards", 1, columns())
    }

    private var gridWidth by mutableStateOf(360.dp)
    private var windowWidth by mutableStateOf(COMPACT_WINDOW_WIDTH)
    private var windowHeight by mutableStateOf(900.dp)

    /** A fixed grid width, so the column count depends on the window class alone. */
    private fun showInFixedGrid() {
        composeRule.setContent {
            DreamDroidTheme {
                WithWindowSize(windowWidth, windowHeight) {
                    Box(Modifier.width(gridWidth)) {
                        ZapScreen(items = TWO_SERVICES, onItemClick = {}, onItemLongClick = {})
                    }
                }
            }
        }
    }

    /** In 360dp: two at 70dp and 90dp cards, one at 105dp. In 280dp: two at 70dp, one above. */
    private fun columns(): Int {
        composeRule.waitForIdle()
        return if (top("ZDF HD") > top("ARD HD")) 1 else 2
    }

    private fun top(name: String) =
        composeRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ZapScreen(
                    items = emptyList(),
                    onItemClick = {},
                    onItemLongClick = {},
                    emptyMessage = "No items to display…"
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }

    private companion object {
        val TWO_SERVICES = listOf(
            Service("1:0:1:1:1:1:1:0:0:0:", "ARD HD"),
            Service("1:0:1:2:1:1:1:0:0:0:", "ZDF HD")
        )
    }
}
