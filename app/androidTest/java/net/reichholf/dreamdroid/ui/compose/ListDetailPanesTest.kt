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
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.testutil.COMPACT_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.LARGE_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [ListDetailPanes] by window size: two panes, a list that waits for a detail, one pane on a narrow
 * or short window.
 */
class ListDetailPanesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var detail by mutableStateOf<String?>(null)
    private var extra by mutableStateOf<String?>(null)

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

    @Test
    fun anExtraPaneSitsBesideTheDetailAndBackClosesItFirst() {
        detail = "Tagesschau"
        extra = "heute"
        show(EXPANDED_WINDOW_WIDTH, withEmptyDetail = false)

        val pane = composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG)
            .fetchSemanticsNode().boundsInRoot
        val extraPane = composeRule.onNodeWithTag(LIST_DETAIL_EXTRA_PANE_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("extra pane beside the detail", extraPane.left >= pane.right)
        composeRule.onNodeWithText("Extra heute").assertIsDisplayed()
        composeRule.onNodeWithTag(LIST_TAG).assertIsNotDisplayed()

        pressBack()

        assertNull(extra)
        assertEquals("Tagesschau", detail)
        composeRule.onNodeWithText("Extra heute").assertDoesNotExist()
        composeRule.onNodeWithTag(LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun aWindowForThreePanesShowsAllAndClosingTheExtraHidesIt() {
        detail = "Tagesschau"
        extra = "heute"
        show(LARGE_WINDOW_WIDTH, withEmptyDetail = false)

        composeRule.onNodeWithTag(LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Extra heute").assertIsDisplayed()

        // As the pane's close button does.
        extra = null
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Extra heute").assertDoesNotExist()
        composeRule.onNodeWithTag(LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
    }

    @Test
    fun aNarrowWindowLeavesTheExtraPaneToTheCaller() {
        detail = "Tagesschau"
        extra = "heute"
        show(COMPACT_WINDOW_WIDTH, withEmptyDetail = false)

        composeRule.onNodeWithTag(LIST_DETAIL_EXTRA_PANE_TAG).assertDoesNotExist()
        pressBack()

        assertEquals("heute", extra)
        assertNull(detail)
    }

    @Test
    fun aPhoneInLandscapeKeepsOnePane() {
        detail = "Tagesschau"
        show(EXPANDED_WINDOW_WIDTH, withEmptyDetail = true, height = PHONE_LANDSCAPE_HEIGHT)

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Single Tagesschau").assertIsDisplayed()
    }

    @Test
    fun aTabletInLandscapeShowsTwoPanes() {
        detail = "Tagesschau"
        show(EXPANDED_WINDOW_WIDTH, withEmptyDetail = true, height = TABLET_LANDSCAPE_HEIGHT)

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Detail Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Single Tagesschau").assertDoesNotExist()
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun show(width: Dp, withEmptyDetail: Boolean, height: Dp = 900.dp) {
        composeRule.setContent {
            WithWindowSize(width, height) {
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
                        singlePaneDetail = { Text("Single $it") },
                        extraPane = extra?.let { { Text("Extra $it") } },
                        onExtraDismiss = { extra = null }
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

        /** Compact height: a phone in landscape is under 480dp tall. */
        val PHONE_LANDSCAPE_HEIGHT = 400.dp

        /** Medium height, as a tablet in landscape. */
        val TABLET_LANDSCAPE_HEIGHT = 800.dp
    }
}
