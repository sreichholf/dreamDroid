package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.services.TimerListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvTimerListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addRowAndNamesServicesAreDisplayedAndEnabledIsSelected() {
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvTimerListScreen(
                        items = sampleTimers(),
                        onAdd = {},
                        onToggleEnabled = {},
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("tv_timers_list").assertExists()
        composeRule.onNodeWithTag("tv_timers_add").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("New Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Evening news").assertIsDisplayed()
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 – 20:15").assertIsDisplayed()
        composeRule.onNodeWithText("Movie night").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF").assertIsDisplayed()
        composeRule.onNodeWithText("21:00 – 23:00").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_timers_row_0").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("tv_timers_row_1")
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assertHasClickAction()
        composeRule.onNodeWithTag("tv_timers_edit_0").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("tv_timers_delete_1")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addRowInvokesOnAdd() {
        var added = false
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvTimerListScreen(
                        items = sampleTimers(),
                        onAdd = { added = true },
                        onToggleEnabled = {},
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_timers_add") { added }
        assertEquals(true, added)
    }

    @Test
    fun directionCenterOnDisabledRowInvokesOnToggleEnabled() {
        var toggled: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvTimerListScreen(
                        items = sampleTimers(),
                        onAdd = {},
                        onToggleEnabled = { toggled = it },
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_timers_row_1") { toggled != null }
        assertEquals(1, toggled)
    }

    @Test
    fun editAndDeleteTagsFireCallbacks() {
        var edited: Int? = null
        var deleted: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvTimerListScreen(
                        items = sampleTimers(),
                        onAdd = {},
                        onToggleEnabled = {},
                        onEdit = { edited = it },
                        onDelete = { deleted = it },
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_timers_edit_0") { edited != null }
        assertEquals(0, edited)
        dpadActivate("tv_timers_delete_1") { deleted != null }
        assertEquals(1, deleted)
    }

    @Test
    fun deleteConfirmDialogConfirmInvokesOnDeleteConfirmed() {
        var deleted: Int? = null
        var confirmed: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvTimerListScreen(
                        items = sampleTimers(),
                        onAdd = {},
                        onToggleEnabled = {},
                        onEdit = {},
                        onDelete = { deleted = it },
                        onDeleteConfirmed = { confirmed = it }
                    )
                }
            }
        }
        dpadActivate("tv_timers_delete_1") { deleted != null }
        composeRule.onNodeWithText("Really delete?").assertIsDisplayed()
        composeRule.onNode(hasText("Movie night") and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()
        assertEquals(1, confirmed)
        composeRule.onNodeWithText("Really delete?").assertDoesNotExist()
    }

    private fun dpadActivate(tag: String, invoked: () -> Boolean) {
        val node = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (!invoked()) {
            node.performClick()
            composeRule.waitForIdle()
        }
    }
}

@Composable
private fun SizedHost(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        content()
    }
}

private fun sampleTimers() = listOf(
    TimerListItem(
        index = 0,
        name = "Evening news",
        serviceName = "ARD",
        begin = "20:00",
        end = "20:15",
        action = "Record",
        state = "Waiting",
        stateColor = 0,
        enabled = true
    ),
    TimerListItem(
        index = 1,
        name = "Movie night",
        serviceName = "ZDF",
        begin = "21:00",
        end = "23:00",
        action = "Record",
        state = "Disabled",
        stateColor = 1,
        enabled = false
    )
)
