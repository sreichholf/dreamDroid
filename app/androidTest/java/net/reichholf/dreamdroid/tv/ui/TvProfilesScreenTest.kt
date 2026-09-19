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
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun namesHostsAndTagsAreDisplayedAndActiveIsSelected() {
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvProfilesScreen(
                        profiles = sampleProfiles(),
                        onAdd = {},
                        onActivate = {},
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("tv_profiles_list").assertExists()
        composeRule.onNodeWithTag("tv_profiles_add").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Add Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("dm7080.local").assertIsDisplayed()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.50").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_profiles_row_1").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("tv_profiles_row_2")
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assertHasClickAction()
        composeRule.onNodeWithTag("tv_profiles_edit_1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("tv_profiles_delete_2")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addRowInvokesOnAdd() {
        var added = false
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvProfilesScreen(
                        profiles = sampleProfiles(),
                        onAdd = { added = true },
                        onActivate = {},
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_profiles_add") { added }
        assertEquals(true, added)
    }

    @Test
    fun directionCenterOnNonActiveRowInvokesOnActivate() {
        var activated: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvProfilesScreen(
                        profiles = sampleProfiles(),
                        onAdd = {},
                        onActivate = { activated = it },
                        onEdit = {},
                        onDelete = {},
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_profiles_row_2") { activated != null }
        assertEquals(2, activated)
    }

    @Test
    fun editAndDeleteTagsFireCallbacks() {
        var edited: Int? = null
        var deleted: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvProfilesScreen(
                        profiles = sampleProfiles(),
                        onAdd = {},
                        onActivate = {},
                        onEdit = { edited = it },
                        onDelete = { deleted = it },
                        onDeleteConfirmed = {}
                    )
                }
            }
        }
        dpadActivate("tv_profiles_edit_1") { edited != null }
        assertEquals(1, edited)
        dpadActivate("tv_profiles_delete_2") { deleted != null }
        assertEquals(2, deleted)
    }

    @Test
    fun deleteConfirmDialogConfirmInvokesOnDeleteConfirmed() {
        var deleted: Int? = null
        var confirmed: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                SizedHost {
                    TvProfilesScreen(
                        profiles = sampleProfiles(),
                        onAdd = {},
                        onActivate = {},
                        onEdit = {},
                        onDelete = { deleted = it },
                        onDeleteConfirmed = { confirmed = it }
                    )
                }
            }
        }
        dpadActivate("tv_profiles_delete_2") { deleted != null }
        composeRule.onNodeWithText("Do you really want to delete this profile?")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()
        assertEquals(2, confirmed)
        composeRule.onNodeWithText("Do you really want to delete this profile?")
            .assertDoesNotExist()
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

private fun sampleProfiles() = listOf(
    ProfileListItem(
        id = 1,
        name = "Living Room",
        host = "dm7080.local",
        active = true
    ),
    ProfileListItem(
        id = 2,
        name = "Bedroom",
        host = "192.168.1.50",
        active = false
    )
)
