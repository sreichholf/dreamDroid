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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
     * reload must keep tab labels readable — the old [androidx.compose.material3.pulltorefresh.PullToRefreshContainer]
     * elevated dark disk sat under Provider even when clipped (drawn inside the list host).
     */
    @Test
    fun refreshingBelowTabsKeepsBouquetTabLabelsVisible() {
        composeRule.setContent {
            DreamDroidTheme {
                Column(Modifier.fillMaxSize()) {
                    var selected by remember { mutableIntStateOf(0) }
                    val tabs = listOf("Favourites (TV)", "Provider", "All Services")
                    ScrollableTabRow(
                        selectedTabIndex = selected,
                        modifier = Modifier.testTag("hub_tabs"),
                    ) {
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
        composeRule.onNodeWithTag(PULL_REFRESH_INDICATOR_TAG).assertIsDisplayed()

        val tabBounds = composeRule.onNodeWithTag("hub_tabs").getBoundsInRoot()
        val pullBounds = composeRule.onNodeWithTag("hub_pull").getBoundsInRoot()
        assertTrue(
            "pull host must start at or below the tab row (tab.bottom=${tabBounds.bottom}, pull.top=${pullBounds.top})",
            pullBounds.top.value >= tabBounds.bottom.value - 1f,
        )

        // Root pixels over the Provider label must stay readable text contrast — not a flat
        // elevated surfaceContainerHigh disk painted over the tab (the #351 clip-only failure).
        val providerBounds = composeRule.onNodeWithText("Provider").getBoundsInRoot()
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        val rootBitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val rootWidthDp = (rootBounds.right - rootBounds.left).value
        val density = rootBitmap.width / rootWidthDp
        val left = (providerBounds.left.value * density).toInt().coerceIn(0, rootBitmap.width - 1)
        val top = (providerBounds.top.value * density).toInt().coerceIn(0, rootBitmap.height - 1)
        val right = (providerBounds.right.value * density).toInt().coerceIn(left + 1, rootBitmap.width)
        val bottom = (providerBounds.bottom.value * density).toInt().coerceIn(top + 1, rootBitmap.height)
        var distinctColors = 0
        val seen = HashSet<Int>()
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                seen.add(rootBitmap.getPixel(x, y))
                x += 2
            }
            y += 2
        }
        distinctColors = seen.size
        assertTrue(
            "Provider tab region should keep text contrast during reload, not a flat PTR disk (colors=$distinctColors)",
            distinctColors >= 3,
        )
    }
}
