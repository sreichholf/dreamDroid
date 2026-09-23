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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
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
}
