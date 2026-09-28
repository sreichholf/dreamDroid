package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
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
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsDemoRow() {
        composeRule.setContent {
            DreamDroidTheme {
                ProfilesScreen(
                    profiles = listOf(
                        ProfileListItem(
                            id = 1,
                            name = "Demo",
                            host = "dreamdroid.org",
                            active = true
                        )
                    ),
                    onProfileClick = {},
                    onProfileEdit = {}
                )
            }
        }
        composeRule.onNodeWithText("Demo", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithText("dreamdroid.org").assertIsDisplayed()
        val tiles = composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)
        tiles.assertCountEquals(1)
        tiles[0].assertLeftPositionInRootIsEqualTo(8.dp)
    }

    @Test
    fun tapActivatesAndEditIconOpensEditor() {
        var clickedId = -1
        var editedId = -1
        composeRule.setContent {
            DreamDroidTheme {
                ProfilesScreen(
                    profiles = listOf(
                        ProfileListItem(
                            id = 1,
                            name = "Demo",
                            host = "dreamdroid.org",
                            active = true
                        )
                    ),
                    onProfileClick = { clickedId = it.id },
                    onProfileEdit = { editedId = it.id }
                )
            }
        }
        composeRule.onNodeWithText("Demo", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, clickedId)
        assertEquals(-1, editedId)
        clickedId = -1
        composeRule.onNodeWithTag(PROFILE_ROW_EDIT_TAG_PREFIX + "1").performClick()
        composeRule.waitForIdle()
        assertEquals(-1, clickedId)
        assertEquals(1, editedId)
    }

    @Test
    fun profileRowsAreInsetTonalTilesWithAGap() {
        composeRule.setContent {
            DreamDroidTheme {
                ProfilesScreen(
                    profiles = listOf(
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
                    ),
                    onProfileClick = {},
                    onProfileEdit = {}
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)
        tiles.assertCountEquals(2)
        tiles[0].assertLeftPositionInRootIsEqualTo(8.dp)
        val gap = tiles[1].getBoundsInRoot().top - tiles[0].getBoundsInRoot().bottom
        assertTrue("expected a gutter between tiles, gap=$gap", gap >= 3.dp)
    }
}
