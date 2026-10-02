package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.BouquetMode
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BouquetContentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun rendersEachKindOfEntry() {
        show(ready())

        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNode(
            hasText("News") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Alternatives group. Edit it on the receiver.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(
            BOUQUET_DRAG_HANDLE_TAG,
            useUnmergedTree = true
        ).assertCountEquals(4)
    }

    @Test
    fun failureShowsItsOwnMessage() {
        show(state().copy(content = BouquetContentList.Failed(UiText.Raw("Timeout"))))

        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertIsDisplayed()
    }

    @Test
    fun insertMarkerFromTheRowMenu() {
        var state by mutableStateOf(ready())
        val picked = mutableListOf<Pair<BouquetContentRow, BouquetEntryAction>>()
        composeRule.setContent {
            DreamDroidTheme {
                BouquetContentScreen(
                    state = state,
                    onRefresh = {},
                    onMenu = { row ->
                        state = state.copy(
                            menu = RowMenuState(
                                row.key,
                                listOf(BouquetEntryAction.Rename, BouquetEntryAction.InsertMarker)
                            )
                        )
                    },
                    onMenuAction = { row, action -> picked += row to action },
                    onMenuDismiss = { state = state.copy(menu = null) },
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[2].performClick()
        composeRule.onNodeWithText("Insert marker above").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ROWS[2] to BouquetEntryAction.InsertMarker), picked)
            assertEquals(null, state.menu)
        }
    }

    @Test
    fun accessibilityActionsMoveTheRow() {
        val moves = mutableListOf<Pair<Int, Int>>()
        show(ready(), onMove = { key, position -> moves += key to position })

        val row = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
                hasAnyDescendant(hasText("News"))
        ).fetchSemanticsNode()
        val actions = row.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move up", "Move down"), actions.map { it.label })
        composeRule.runOnIdle { actions[1].action() }

        composeRule.runOnIdle { assertEquals(listOf(ROWS[1].key to 2), moves) }
    }

    @Test
    fun dragByTheHandleMovesTheRowOnDrop() {
        val moves = mutableListOf<Pair<Int, Int>>()
        show(ready(), onMove = { key, position -> moves += key to position })

        // ListItem merges its content, so the handles live in the unmerged tree.
        val handles = composeRule.onAllNodesWithTag(BOUQUET_DRAG_HANDLE_TAG, useUnmergedTree = true)
        val from = handles[0].fetchSemanticsNode().boundsInRoot.center
        val to = handles[2].fetchSemanticsNode().boundsInRoot.center
        handles[0].performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy((to - from) / DRAG_STEPS.toFloat()) }
            // Hold before lifting, so the list lays out the last swap first.
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(ROWS[0].key to 2), moves) }
        // The fake did not take the move, so the rows are back in their order.
        val first = composeRule.onNodeWithText("Das Erste HD").fetchSemanticsNode()
        val stream = composeRule.onNodeWithText("RBTV").fetchSemanticsNode()
        assertTrue(first.boundsInRoot.top < stream.boundsInRoot.top)
    }

    @Test
    fun blockedListOffersNoMoves() {
        show(ready().copy(blocked = true))

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .assertCountEquals(0)
    }

    @Test
    fun removeAsksFirst() {
        var removed = 0
        showDialogs(ready().copy(dialog = BouquetContentDialog.Remove(ROWS[0])), onRemove = {
            removed++
        })

        composeRule.onNodeWithText("Remove \"Das Erste HD\" from this bouquet?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.runOnIdle { assertEquals(1, removed) }
    }

    @Test
    fun markerDialogShowsTheNameError() {
        showDialogs(
            ready().copy(
                dialog = BouquetContentDialog.AddMarker(before = null),
                nameError = UiText.Raw("Empty")
            )
        )

        composeRule.onNodeWithText("Add marker").assertIsDisplayed()
        composeRule.onNodeWithText("Empty").assertIsDisplayed()
    }

    private fun show(state: BouquetContentUiState, onMove: (Int, Int) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            DreamDroidTheme {
                BouquetContentScreen(
                    state = state,
                    onRefresh = {},
                    onMenu = {},
                    onMenuAction = { _, _ -> },
                    onMenuDismiss = {},
                    onMove = onMove
                )
            }
        }
    }

    private fun showDialogs(state: BouquetContentUiState, onRemove: () -> Unit = {}) {
        composeRule.setContent {
            DreamDroidTheme {
                BouquetContentDialogs(
                    state = state,
                    nameState = TextFieldState(),
                    onConfirmRename = {},
                    onConfirmAddMarker = {},
                    onConfirmRemove = onRemove,
                    onDismiss = {}
                )
            }
        }
    }

    private fun state() = BouquetContentUiState(
        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.fav.tv\" ORDER BY bouquet",
        bouquetName = "Favourites (TV)",
        mode = BouquetMode.Tv
    )

    private fun ready() = state().copy(content = BouquetContentList.Ready(ROWS))

    private companion object {
        const val DRAG_STEPS = 10
        const val HOLD_MILLIS = 200L

        val ROWS = listOf(
            BouquetEntry(
                "1:0:19:283D:3FB:1:C00000:0:0:0:",
                "Das Erste HD",
                BouquetEntryKind.Service
            ),
            BouquetEntry("1:64:1:0:0:0:0:0:0:0::News", "News", BouquetEntryKind.Marker),
            BouquetEntry(
                "8193:0:0:0:0:0:0:0:0:0:yt%3a//live/rocketbeanstv:RBTV",
                "RBTV",
                BouquetEntryKind.Stream
            ),
            BouquetEntry(
                "1:134:1:0:0:0:0:0:0:0:FROM BOUQUET \"alternatives.zdf.tv\" ORDER BY bouquet",
                "ZDF HD",
                BouquetEntryKind.Alternative
            )
        ).mapIndexed { index, entry -> BouquetContentRow(index, entry) }
    }
}
