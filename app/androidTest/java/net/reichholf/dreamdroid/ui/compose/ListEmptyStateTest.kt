package net.reichholf.dreamdroid.ui.compose

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ListEmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun loadingShowsProgressNotMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ListEmptyState(loading = true, message = "Loading…")
            }
        }
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Loading…").assertDoesNotExist()
    }

    @Test
    fun messageAndRetryInvokeCallback() {
        val retries = AtomicInteger(0)
        composeRule.setContent {
            DreamDroidTheme {
                ListEmptyState(
                    loading = false,
                    message = "No items to display…",
                    onRetry = { retries.incrementAndGet() }
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertIsDisplayed().performClick()
        assertEquals(1, retries.get())
    }
}
