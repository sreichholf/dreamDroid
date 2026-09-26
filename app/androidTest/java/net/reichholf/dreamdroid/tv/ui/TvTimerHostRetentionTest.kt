package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class TvTimerHostRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reenteringTimersPaintsRetainedListWhileRefreshing() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TvTimerHostViewModel(app)
        val refresh = CompletableDeferred<TvTimerLoadPaint>()
        var loads = 0
        viewModel.loadPaint = {
            loads++
            if (loads == 1) {
                TvTimerLoadPaint(listOf(Timer(name = "Retained", serviceName = "ARD")), true, null)
            } else {
                refresh.await()
            }
        }
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    if (shown) {
                        TvTimerHost(viewModel = viewModel)
                    }
                }
            }
        }
        composeRule.waitUntil { viewModel.items.isNotEmpty() }
        composeRule.onNodeWithText("Retained").assertIsDisplayed()

        shown = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_timers_host").assertDoesNotExist()

        shown = true
        composeRule.waitForIdle()
        assertEquals(2, loads)
        composeRule.onNodeWithText("Retained").assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.loading)).assertDoesNotExist()

        refresh.complete(
            TvTimerLoadPaint(listOf(Timer(name = "Refreshed", serviceName = "ZDF")), true, null)
        )
        composeRule.waitUntil { viewModel.items.firstOrNull()?.name == "Refreshed" }
        composeRule.onNodeWithText("Refreshed").assertIsDisplayed()
    }

    @Test
    fun reenteringTimersKeepsAddEditor() {
        val seededLocation = ProfileRepository.get().locations().isEmpty()
        val seededTag = ProfileRepository.get().tags().isEmpty()
        if (seededLocation) {
            ProfileRepository.get().locations().add("/hdd/movie/")
        }
        if (seededTag) {
            ProfileRepository.get().tags().add("News")
        }
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = TvTimerHostViewModel(app)
            viewModel.loadPaint = { TvTimerLoadPaint(emptyList(), true, null) }
            var shown by mutableStateOf(true)
            composeRule.setContent {
                DreamDroidTvTheme {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        if (shown) {
                            TvTimerHost(viewModel = viewModel)
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("tv_timers_add").assertIsDisplayed()
            composeRule.runOnIdle { viewModel.showAdd() }
            assertAddEditorShowing()
            assertEquals(TvTimerPage.Add, viewModel.page)

            shown = false
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("tv_timers_host").assertDoesNotExist()

            val draft = viewModel.editorTimer
            shown = true
            composeRule.waitForIdle()
            assertEquals(TvTimerPage.Add, viewModel.page)
            assertSame(draft, viewModel.editorTimer)
            assertAddEditorShowing()
        } finally {
            if (seededLocation) {
                ProfileRepository.get().locations().remove("/hdd/movie/")
            }
            if (seededTag) {
                ProfileRepository.get().tags().remove("News")
            }
        }
    }

    private fun assertAddEditorShowing() {
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_timers_list").assertDoesNotExist()
        composeRule.onNodeWithTag("tv_timers_add").assertDoesNotExist()
    }
}
