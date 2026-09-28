package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The stateless TV picker screen. The picker's state (open bouquet, loaded channels, reopen)
 * is covered by the JVM `TimerServicePickViewModelTest`.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerServicePickTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenShowsRowsAndDeliversDpadClick() {
        var clicked: Service? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                TvTimerServicePickScreen(
                    rows = listOf(CHANNEL),
                    emptyMessage = null,
                    onRowClick = { clicked = it }
                )
            }
        }
        pressCenterOn(CHANNEL.name)
        assertEquals(CHANNEL, clicked)
    }

    @Test
    fun screenShowsEmptyMessageWithoutRows() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvTimerServicePickScreen(
                    rows = emptyList(),
                    emptyMessage = "Nothing here",
                    onRowClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    /** TV Surfaces handle D-pad center; a touch [performClick] is ignored. */
    private fun pressCenterOn(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    private companion object {
        val CHANNEL = Service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD")
    }
}
