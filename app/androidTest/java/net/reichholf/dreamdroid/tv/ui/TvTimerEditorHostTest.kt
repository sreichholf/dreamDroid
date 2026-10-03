package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * The TV add/edit editor, stateless. [TvTimerEditViewModel] behavior (keeping edits across
 * a configuration change, starting over after release) is in its JVM test.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerEditorHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createModeShowsTimerNameAndSaveFab() {
        composeRule.setContent {
            Editor(state(isCreate = true), name = "Sample")
        }

        composeRule.onNodeWithText("Sample").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(saveLabel()).assertIsDisplayed()
    }

    @Test
    fun saveFabSaves() {
        var saves = 0
        composeRule.setContent {
            Editor(state(isCreate = true), name = "Sample", onSave = { saves++ })
        }

        composeRule.onNodeWithContentDescription(saveLabel()).performClick()

        assertEquals(1, saves)
    }

    @Test
    fun blockedSaveShowsNeedsReceiverAndDoesNotSaveOrDismiss() {
        var saves = 0
        var dismissed = false
        composeRule.setContent {
            Editor(
                state(isCreate = true).copy(mutationsBlocked = true),
                name = "Blocked save",
                onSave = { saves++ },
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithContentDescription(saveLabel()).performClick()
        composeRule.waitForIdle()
        assertEquals(0, saves)
        assertFalse(dismissed)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
        dismissNeedsReceiver()
        composeRule.onNodeWithTag("hub_stream_unavailable").assertDoesNotExist()
        assertEquals(0, saves)
        assertFalse(dismissed)
    }

    @Test
    fun editModeShowsExistingTimerName() {
        composeRule.setContent {
            Editor(state(isCreate = false), name = "Tagesschau")
        }

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
    }

    @Test
    fun vpsFieldIsHiddenWithoutThePlugin() {
        val timer = sampleTimer().copy(vps = TimerVps(VpsMode.Safe))
        composeRule.setContent {
            Editor(state(isCreate = true).copy(timer = timer), name = "Sample")
        }

        composeRule.onNodeWithContentDescription(string(R.string.vps)).assertDoesNotExist()
    }

    @Test
    fun manualTimerWithVpsShowsTheNoteAndTheVpsTime() {
        val timer = sampleTimer().copy(eit = "", vps = TimerVps(VpsMode.Safe))
        composeRule.setContent {
            Editor(state(isCreate = true).copy(timer = timer, vpsPlugin = true), name = "Sample")
        }

        composeRule.onNodeWithContentDescription(string(R.string.vps))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.vps_note))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.vps_time))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun vpsOffHidesTheNoteAndTheVpsTime() {
        val timer = sampleTimer().copy(eit = "", vps = TimerVps(VpsMode.Off))
        composeRule.setContent {
            Editor(state(isCreate = true).copy(timer = timer, vpsPlugin = true), name = "Sample")
        }

        composeRule.onNodeWithContentDescription(string(R.string.vps))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.vps_note)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.vps_time)).assertDoesNotExist()
    }

    @Test
    fun backDismisses() {
        var dismissed = false
        composeRule.setContent {
            Editor(state(isCreate = false), name = "Tagesschau", onDismiss = { dismissed = true })
        }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.runOnIdle { assertEquals(true, dismissed) }
    }

    /**
     * OK is a TV Surface. It handles D-pad center. Android [performClick] is a
     * touch click, which that surface ignores.
     */
    private fun dismissNeedsReceiver() {
        val ok = composeRule.onNodeWithTag("hub_stream_unavailable_ok")
        ok.assertIsDisplayed()
        ok.requestFocus()
        ok.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (
            composeRule.onAllNodesWithTag("hub_stream_unavailable")
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            ok.performClick()
            composeRule.waitForIdle()
        }
    }

    @Composable
    private fun Editor(
        state: TimerEditUiState,
        name: String,
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        DreamDroidTvTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                TvTimerEditorContent(
                    uiState = state,
                    name = remember { TextFieldState(name) },
                    description = remember { TextFieldState("Desc") },
                    actions = object : TimerFormActions {},
                    servicePickKey = tvTimerServicePickKey(null),
                    onServicePicked = {},
                    onSave = onSave,
                    onDismiss = onDismiss
                )
            }
        }
    }

    private fun state(isCreate: Boolean) = TimerEditUiState(
        timer = sampleTimer(),
        isCreate = isCreate,
        locations = listOf("/hdd/movie/"),
        tags = listOf("News")
    )

    private fun saveLabel(): String = string(R.string.save)

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun sampleTimer(): Timer = TimerHelper.getInitialTimer().copy(
        name = "Launch name",
        description = "Desc",
        serviceName = "Das Erste HD",
        reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
        disabled = "0",
        justPlay = "0",
        afterEvent = "3",
        location = "/hdd/movie/",
        repeated = "0",
        tags = ""
    )
}
