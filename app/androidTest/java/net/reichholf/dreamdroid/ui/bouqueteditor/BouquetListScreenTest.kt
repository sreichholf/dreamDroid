package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
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
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BouquetListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun rendersTheBouquetsUnderTheModeSwitch() {
        show(ready())

        composeRule.onNodeWithText("TV").assertIsSelected()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Favourites (TV)").assertIsDisplayed()
        composeRule.onNodeWithText("Sports (TV)").assertIsDisplayed()
        composeRule.onAllNodesWithTag(
            BOUQUET_DRAG_HANDLE_TAG,
            useUnmergedTree = true
        ).assertCountEquals(2)
    }

    @Test
    fun tapOpensTheBouquet() {
        val opened = mutableListOf<BouquetEntry>()
        show(ready(), onOpenBouquet = { opened += it })

        composeRule.onNode(hasText("Sports (TV)") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(listOf(SPORTS), opened) }
    }

    @Test
    fun missingPluginExplainsWhyAndOffersAReload() {
        var reloads = 0
        show(BouquetListUiState(content = BouquetListContent.NotInstalled), onRefresh = {
            reloads++
        })

        composeRule.onNodeWithText(
            "Editing bouquets needs the WebBouquetEditor plugin for the receiver's web " +
                "interface (package enigma2-plugin-extensions-webbouqueteditor). This " +
                "receiver does not have it installed. Install it on the receiver, then tap " +
                "Reload."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Reload").performClick()
        composeRule.runOnIdle { assertEquals(1, reloads) }
    }

    @Test
    fun failureShowsItsOwnMessage() {
        show(BouquetListUiState(content = BouquetListContent.Failed(UiText.Raw("Timeout"))))

        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertIsDisplayed()
    }

    @Test
    fun moveDownFromTheRowMenu() {
        var state by mutableStateOf(ready())
        val picked = mutableListOf<Pair<BouquetEntry, BouquetRowAction>>()
        composeRule.setContent {
            DreamDroidTheme {
                BouquetListScreen(
                    state = state,
                    onModeChange = {},
                    onRefresh = {},
                    onMenu = { bouquet ->
                        state = state.copy(
                            menu = RowMenuState(
                                bouquet.reference,
                                listOf(BouquetRowAction.MoveDown, BouquetRowAction.Remove)
                            )
                        )
                    },
                    onMenuAction = { bouquet, action -> picked += bouquet to action },
                    onMenuDismiss = { state = state.copy(menu = null) },
                    onMove = { _, _ -> },
                    onOpenBouquet = {}
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("More options")[0].performClick()
        composeRule.onNodeWithText("Move down").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FAVOURITES to BouquetRowAction.MoveDown), picked)
            assertEquals(null, state.menu)
        }
    }

    @Test
    fun accessibilityActionsMoveTheRow() {
        val moves = mutableListOf<Pair<String, Int>>()
        show(ready(), onMove = { ref, position -> moves += ref to position })

        val row = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
                hasAnyDescendant(hasText("Sports (TV)"))
        ).fetchSemanticsNode()
        val actions = row.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move up"), actions.map { it.label })
        composeRule.runOnIdle { actions.single().action() }

        composeRule.runOnIdle { assertEquals(listOf(SPORTS.reference to 0), moves) }
    }

    @Test
    fun dragByTheHandleMovesTheRowOnDrop() {
        val moves = mutableListOf<Pair<String, Int>>()
        show(ready(), onMove = { ref, position -> moves += ref to position })

        // ListItem merges its content, so the handles live in the unmerged tree.
        val handles = composeRule.onAllNodesWithTag(BOUQUET_DRAG_HANDLE_TAG, useUnmergedTree = true)
        val from = handles[1].fetchSemanticsNode().boundsInRoot.center
        val to = handles[0].fetchSemanticsNode().boundsInRoot.center
        handles[1].performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy((to - from) / DRAG_STEPS.toFloat()) }
            // Hold before lifting, so the list lays out the last swap first.
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(SPORTS.reference to 0), moves) }
        // The fake did not take the move, so the rows are back in their order.
        val favourites = composeRule.onNodeWithText("Favourites (TV)").fetchSemanticsNode()
        val sports = composeRule.onNodeWithText("Sports (TV)").fetchSemanticsNode()
        assertTrue(favourites.boundsInRoot.top < sports.boundsInRoot.top)
    }

    @Test
    fun removeAsksFirst() {
        var removed = 0
        var dismissed = 0
        composeRule.setContent {
            DreamDroidTheme {
                BouquetListDialogs(
                    state = ready().copy(dialog = BouquetListDialog.Remove(SPORTS)),
                    nameState = TextFieldState(),
                    onConfirmAdd = {},
                    onConfirmRename = {},
                    onConfirmRemove = { removed++ },
                    onDismiss = { dismissed++ }
                )
            }
        }

        composeRule.onNodeWithText("Remove the bouquet \"Sports (TV)\" from the receiver?")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.runOnIdle {
            assertEquals(1, removed)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun nameErrorShowsInTheAddDialog() {
        composeRule.setContent {
            DreamDroidTheme {
                BouquetListDialogs(
                    state = ready().copy(
                        dialog = BouquetListDialog.Add,
                        nameError = UiText.Raw("Taken")
                    ),
                    nameState = TextFieldState("Sports"),
                    onConfirmAdd = {},
                    onConfirmRename = {},
                    onConfirmRemove = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Add bouquet").assertIsDisplayed()
        composeRule.onNodeWithText("Taken").assertIsDisplayed()
    }

    private fun show(
        state: BouquetListUiState,
        onRefresh: () -> Unit = {},
        onMove: (String, Int) -> Unit = { _, _ -> },
        onOpenBouquet: (BouquetEntry) -> Unit = {}
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                BouquetListScreen(
                    state = state,
                    onModeChange = {},
                    onRefresh = onRefresh,
                    onMenu = {},
                    onMenuAction = { _, _ -> },
                    onMenuDismiss = {},
                    onMove = onMove,
                    onOpenBouquet = onOpenBouquet
                )
            }
        }
    }

    private fun ready() = BouquetListUiState(
        mode = BouquetMode.Tv,
        content = BouquetListContent.Ready(listOf(FAVOURITES, SPORTS))
    )

    private companion object {
        const val DRAG_STEPS = 10
        const val HOLD_MILLIS = 200L

        val FAVOURITES = BouquetEntry(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites (TV)",
            BouquetEntryKind.Bouquet
        )
        val SPORTS = BouquetEntry(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet",
            "Sports (TV)",
            BouquetEntryKind.Bouquet
        )
    }
}
