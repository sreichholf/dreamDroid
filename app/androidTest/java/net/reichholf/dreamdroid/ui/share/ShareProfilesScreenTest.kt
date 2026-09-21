package net.reichholf.dreamdroid.ui.share

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MUTATION_PROGRESS_TAG
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShareProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededProfilesShowNameHostAndClick() {
        val first =
            ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = false)
        val second =
            ProfileListItem(id = 2, name = "Bedroom", host = "192.168.1.50", active = false)
        var clicked: ProfileListItem? = null
        composeRule.setContent {
            DreamDroidTheme {
                ShareProfilesScreen(
                    profiles = listOf(first, second),
                    onProfileClick = { clicked = it }
                )
            }
        }
        composeRule.onNodeWithText("Living Room", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithText("dm7080.local").assertIsDisplayed()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG).assertCountEquals(2)
    }

    @Test
    fun progressOverlayShowsLoadingMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ShareProfilesScreen(
                    profiles = listOf(
                        ProfileListItem(
                            id = 1,
                            name = "Living Room",
                            host = "dm7080.local",
                            active = false
                        )
                    ),
                    onProfileClick = {}
                )
                IndeterminateProgressHost(
                    IndeterminateProgressState(message = "Loading")
                )
            }
        }
        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("Loading").assertIsDisplayed()
        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun progressBlocksProfileRowClicks() {
        val profile =
            ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = false)
        val state = ShareProfilesListState(listOf(profile))
        state.progress = IndeterminateProgressState(message = "Loading")
        var clicked: ProfileListItem? = null
        composeRule.setContent {
            DreamDroidTheme {
                ShareProfilesScreen(
                    profiles = state.profiles,
                    onProfileClick = { clicked = it },
                    clicksEnabled = state.progress == null
                )
                IndeterminateProgressHost(state.progress)
            }
        }
        composeRule.onNodeWithText("Living Room").performClick()
        assertEquals(null, clicked)
    }
}
