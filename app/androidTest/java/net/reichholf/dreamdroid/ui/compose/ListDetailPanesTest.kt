package net.reichholf.dreamdroid.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import net.reichholf.dreamdroid.testutil.COMPACT_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [ListDetailPanes] by window width: two panes, a list that waits for a detail, one pane. */
class ListDetailPanesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var detail by mutableStateOf<String?>(null)

    @Test
    fun withoutAnEmptyDetailTheListTakesTheWholeWidthUntilADetailIsShown() {
        show(EXPANDED_WINDOW_WIDTH, withEmptyDetail = false)
        val root = composeRule.onNodeWithTag(HOST_TAG).fetchSemanticsNode().boundsInRoot
        val fullList = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertDoesNotExist()
        assertEquals(root.width, fullList.width, 1f)

        detail = "Tagesschau"
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val pane = composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("list narrowed for the pane", list.width < fullList.width)
        assertTrue("pane beside the list", pane.left >= list.right)
        composeRule.onNodeWithText("Detail Tagesschau").assertIsDisplayed()
    }

    @Test
    fun withAnEmptyDetailThePaneShowsFromTheStart() {
        show(EXPANDED_WINDOW_WIDTH, withEmptyDetail = true)

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing chosen").assertIsDisplayed()
    }

    @Test
    fun oneDetailFillsANarrowWindowAndBackClearsIt() {
        detail = "Tagesschau"
        show(COMPACT_WINDOW_WIDTH, withEmptyDetail = true)

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Single Tagesschau").assertIsDisplayed()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertNull(detail)
        composeRule.onNodeWithText("Single Tagesschau").assertDoesNotExist()
    }

    private fun show(width: Dp, withEmptyDetail: Boolean) {
        composeRule.setContent {
            WithWindowSize(width) {
                Box(Modifier.fillMaxSize().testTag(HOST_TAG)) {
                    ListDetailPanes(
                        detail = detail,
                        onDetailDismiss = { detail = null },
                        list = { Box(Modifier.fillMaxSize().testTag(LIST_TAG)) },
                        emptyDetail = if (withEmptyDetail) {
                            { Text("Nothing chosen") }
                        } else {
                            null
                        },
                        singlePaneDetail = { Text("Single $it") }
                    ) {
                        Text("Detail $it")
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val HOST_TAG = "list_detail_host"
        const val LIST_TAG = "list_detail_list"
    }
}
