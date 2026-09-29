package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SendMessageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsMessageTypeTimeoutLabels() {
        val viewModel = SendMessageViewModel(SavedStateHandle())
        composeRule.setContent {
            DreamDroidTheme {
                SendMessageHost(viewModel)
            }
        }
        composeRule.onNodeWithText("Enter your message text").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithText("Info").assertIsDisplayed()
        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText("seconds").assertIsDisplayed()
        composeRule.onNodeWithText("20").assertIsDisplayed()
    }

    @Test
    fun typeAndTimeoutStackFullWidth() {
        val viewModel = SendMessageViewModel(SavedStateHandle())
        composeRule.setContent {
            DreamDroidTheme {
                SendMessageHost(viewModel)
            }
        }
        val type = composeRule.onNodeWithText("Type").getBoundsInRoot()
        val timeout = composeRule.onNodeWithText("Timeout").getBoundsInRoot()
        assertTrue(
            "Timeout should stack under Type, type=$type timeout=$timeout",
            timeout.top >= type.bottom
        )
    }

    @Test
    fun typedTextLandsInTheViewModelAndTheTimeoutTakesTwoDigits() {
        val viewModel = SendMessageViewModel(SavedStateHandle())
        composeRule.setContent {
            DreamDroidTheme {
                SendMessageHost(viewModel)
            }
        }
        composeRule.onNodeWithText("Enter your message text").performTextInput("Hello")
        composeRule.onNodeWithText("20").performTextReplacement("123")
        composeRule.onNodeWithText("20").performTextReplacement("4x")
        composeRule.onNodeWithText("20").performTextReplacement("45")
        composeRule.runOnIdle {
            assertEquals("Hello", viewModel.message.text)
            assertEquals("45", viewModel.timeout.text)
        }
    }

    @Composable
    private fun SendMessageHost(viewModel: SendMessageViewModel) {
        val uiState by viewModel.uiState.collectAsState()
        SendMessageScreen(
            message = viewModel.message.state,
            timeout = viewModel.timeout.state,
            typeIndex = uiState.typeIndex,
            onTypeSelected = viewModel::onTypeSelected
        )
    }
}
