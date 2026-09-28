package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The TV Timers header, stateless. Keeping the list and the open editor across leaving
 * the header is [TvTimerHostViewModel]'s, covered by its JVM test.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerHostContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun loadedListShowsWithoutTheLoadingText() {
        composeRule.setContent {
            Host(
                TvTimerHostUiState(
                    timers = listOf(Timer(name = "Retained", serviceName = "ARD")),
                    emptyMessage = null
                )
            )
        }

        composeRule.onNodeWithText("Retained").assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.loading)).assertDoesNotExist()
    }

    @Test
    fun emptyListShowsItsMessage() {
        composeRule.setContent {
            Host(TvTimerHostUiState(emptyMessage = UiText.Resource(R.string.no_list_item)))
        }

        composeRule.onNodeWithTag("tv_timers_add").assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.no_list_item)).assertIsDisplayed()
    }

    @Test
    fun addPageShowsTheEditorInsteadOfTheList() {
        val draft = Timer(name = "Draft")
        composeRule.setContent {
            Host(
                TvTimerHostUiState(page = TvTimerPage.Add, editorTimer = draft, emptyMessage = null)
            ) { timer, isCreate ->
                EditorStub(timer, isCreate)
            }
        }

        composeRule.onNodeWithTag("editor_Draft_true").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_timers_list").assertDoesNotExist()
        composeRule.onNodeWithTag("tv_timers_add").assertDoesNotExist()
    }

    @Test
    fun editPageOpensTheEditorForAnExistingTimer() {
        val timer = Timer(name = "Existing")
        composeRule.setContent {
            Host(
                TvTimerHostUiState(
                    page = TvTimerPage.Edit(0),
                    editorTimer = timer,
                    timers = listOf(timer),
                    emptyMessage = null
                )
            ) { editing, isCreate ->
                EditorStub(editing, isCreate)
            }
        }

        composeRule.onNodeWithTag("editor_Existing_false").assertIsDisplayed()
    }

    @Test
    fun blockedAddShowsNeedsReceiver() {
        var adds = 0
        composeRule.setContent {
            Host(
                TvTimerHostUiState(emptyMessage = null, mutationsBlocked = true),
                onAdd = { adds++ }
            )
        }

        val add = composeRule.onNodeWithTag("tv_timers_add")
        add.requestFocus()
        add.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals(0, adds)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
    }

    @Composable
    private fun Host(
        state: TvTimerHostUiState,
        onAdd: () -> Unit = {},
        editor: @Composable (Timer, Boolean) -> Unit = { _, _ -> }
    ) {
        DreamDroidTvTheme {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                TvTimerHostContent(
                    uiState = state,
                    onAdd = onAdd,
                    onEdit = {},
                    onToggleEnabled = {},
                    onDelete = {},
                    onShowList = {},
                    editor = editor
                )
            }
        }
    }

    @Composable
    private fun EditorStub(timer: Timer, isCreate: Boolean) {
        Box(Modifier.fillMaxWidth().height(10.dp).testTag("editor_${timer.name}_$isCreate"))
    }
}
