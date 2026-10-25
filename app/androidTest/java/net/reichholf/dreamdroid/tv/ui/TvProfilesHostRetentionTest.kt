package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvProfilesHostRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensOnTheProfileList() {
        val viewModel = TvProfilesHostViewModel(memoryProfiles())
        composeRule.setContent {
            DreamDroidTvTheme {
                TvProfilesHost(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithTag("tv_profiles_list").assertExists()
        composeRule.onNodeWithTag("tv_profiles_add").assertExists()
    }

    @Test
    fun addSavesWithTheTvSaveButton() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TvProfilesHostViewModel(memoryProfiles())
        viewModel.showAdd()
        lateinit var inputModeManager: InputModeManager
        var shown by mutableStateOf(false)
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTvTheme {
                if (shown) {
                    TvProfilesHost(viewModel = viewModel)
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
            shown = true
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .performScrollTo()
            .performTextReplacement("TV Box")
        composeRule.onNodeWithContentDescription(app.getString(R.string.host_long))
            .performScrollTo()
            .performTextReplacement("192.168.0.10")

        // TV Save is a button at the end of the form, not the phone FAB.
        composeRule.onNodeWithContentDescription(app.getString(R.string.save))
            .assertDoesNotExist()
        val save = composeRule.onNodeWithText(app.getString(R.string.save))
        save.performScrollTo().assertIsDisplayed()
        save.requestFocus()
        save.assertIsFocused()
        save.performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.page == TvProfilesPage.List &&
                viewModel.uiState.value.profiles.any { it.name == "TV Box" }
        }
        composeRule.runOnIdle {
            assertEquals("192.168.0.10", viewModel.uiState.value.profiles.single().host)
        }
        composeRule.onNodeWithText("TV Box").assertIsDisplayed()
    }

    @Test
    fun leavingCompositionKeepsAddDraft() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TvProfilesHostViewModel(memoryProfiles())
        viewModel.showAdd()
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    if (shown) {
                        TvProfilesHost(viewModel = viewModel)
                    }
                }
            }
        }
        composeRule.onNodeWithTag("tv_profiles_list").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .performTextReplacement("Draft Box")
        composeRule.onNodeWithText("Draft Box").assertIsDisplayed()

        shown = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Draft Box").assertDoesNotExist()

        shown = true
        composeRule.waitForIdle()
        assertEquals("Draft Box", viewModel.fields.name.text)
        composeRule.onNodeWithContentDescription(app.getString(R.string.profile_name))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Draft Box").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_profiles_list").assertDoesNotExist()
    }
}
