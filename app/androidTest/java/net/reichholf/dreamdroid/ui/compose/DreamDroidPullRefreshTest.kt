package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pull-to-refresh must not fire when the list is scrolled away from the top
 * (the old SwipeRefreshLayout+ComposeView bug on service lists).
 */
class DreamDroidPullRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun scrollAwayFromTopDoesNotRefresh() {
        val refreshCount = AtomicInteger(0)
        val labels = (0 until 40).map { "Channel $it" }

        composeRule.setContent {
            DreamDroidTheme {
                DreamDroidPullRefresh(
                    refreshing = false,
                    onRefresh = { refreshCount.incrementAndGet() },
                ) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pull_list"),
                    ) {
                        items(labels) { label ->
                            Text(text = label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Channel 0").assertIsDisplayed()
        composeRule.onNodeWithTag("pull_list").performScrollToIndex(20)
        composeRule.onNodeWithText("Channel 20").assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(0, refreshCount.get())
    }

    @Test
    fun programmaticRefreshingKeepsContentVisible() {
        composeRule.setContent {
            DreamDroidTheme {
                DreamDroidPullRefresh(
                    refreshing = true,
                    onRefresh = {},
                ) {
                    Text("Still here")
                }
            }
        }
        composeRule.onNodeWithText("Still here").assertIsDisplayed()
    }

    /**
     * Hub layout: bouquet [ScrollableTabRow] above the pull-refresh host. Programmatic
     * refresh must keep tab labels readable (the container's dark circular surface used to
     * paint over "Provider").
     */
    @Test
    fun refreshingBelowTabsKeepsBouquetTabLabelsVisible() {
        composeRule.setContent {
            DreamDroidTheme {
                Column(Modifier.fillMaxSize()) {
                    var selected by remember { mutableIntStateOf(0) }
                    val tabs = listOf("Favourites (TV)", "Provider", "All Services")
                    ScrollableTabRow(selectedTabIndex = selected) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = index == selected,
                                onClick = { selected = index },
                                text = { Text(title) },
                            )
                        }
                    }
                    DreamDroidPullRefresh(
                        refreshing = true,
                        onRefresh = {},
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_pull"),
                    ) {
                        Text("list body")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Favourites (TV)").assertIsDisplayed()
        composeRule.onNodeWithText("Provider").assertIsDisplayed()
        composeRule.onNodeWithText("All Services").assertIsDisplayed()
        composeRule.onNodeWithText("list body").assertIsDisplayed()
    }
}
