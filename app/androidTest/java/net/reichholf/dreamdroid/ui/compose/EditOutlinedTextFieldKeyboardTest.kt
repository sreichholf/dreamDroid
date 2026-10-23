package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class EditOutlinedTextFieldKeyboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val host = FocusRequester()
    private val port = FocusRequester()
    private var inputSessions = 0

    @Test
    fun dpadFocusWaitsForOkBeforeOpeningTheKeyboard() {
        setForm(InputMode.Keyboard)

        composeRule.runOnIdle { host.requestFocus() }
        composeRule.onNodeWithContentDescription("Host").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, inputSessions) }

        composeRule.onNodeWithContentDescription("Host").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitUntil { inputSessions == 1 }

        composeRule.runOnIdle { port.requestFocus() }
        composeRule.onNodeWithContentDescription("Port").assertIsFocused()
        composeRule.runOnIdle { assertEquals(1, inputSessions) }
    }

    @Test
    fun touchFocusOpensTheKeyboard() {
        setForm(InputMode.Touch)

        composeRule.runOnIdle { host.requestFocus() }
        composeRule.waitUntil { inputSessions == 1 }
    }

    private fun setForm(inputMode: InputMode) {
        var showForm by mutableStateOf(false)
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            InterceptPlatformTextInput(
                interceptor = { request, nextHandler ->
                    inputSessions++
                    nextHandler.startInputMethod(request)
                }
            ) {
                DreamDroidTheme {
                    if (showForm) {
                        Column {
                            EditOutlinedTextField(
                                state = TextFieldState(),
                                label = "Host",
                                modifier = Modifier.focusRequester(host)
                            )
                            EditOutlinedTextField(
                                state = TextFieldState(),
                                label = "Port",
                                modifier = Modifier.focusRequester(port)
                            )
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(inputMode) }
        composeRule.runOnIdle {
            assertEquals(inputMode, inputModeManager.inputMode)
            showForm = true
        }
        composeRule.waitForIdle()
    }
}
