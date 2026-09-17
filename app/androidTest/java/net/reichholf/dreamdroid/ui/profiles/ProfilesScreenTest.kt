package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @After
    fun deleteF05Profiles() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.profilesBlocking(ctx)
        val keep = dao.getProfiles().firstOrNull {
            it.name?.startsWith("f05-") != true && it.id != null
        }
        dao.getProfiles()
            .filter { it.name?.startsWith("f05-") == true }
            .forEach { dao.deleteProfile(it) }
        if (keep?.id != null) {
            DreamDroid.setCurrentProfile(ctx, keep.id!!, true)
        }
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
                    onProfileLongClick = {}
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
                    onProfileLongClick = {}
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)
        tiles.assertCountEquals(2)
        tiles[0].assertLeftPositionInRootIsEqualTo(8.dp)
        val gap = tiles[1].getBoundsInRoot().top - tiles[0].getBoundsInRoot().bottom
        assertTrue("expected a gutter between tiles, gap=$gap", gap >= 3.dp)
    }

    @Test
    fun deletingActiveProfileDoesNotKeepGoneId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.profilesBlocking(context)
        val keep = Profile.getDefault().apply {
            name = "f05-keep"
            host = "10.0.0.2"
        }
        val gone = Profile.getDefault().apply {
            name = "f05-gone"
            host = "10.0.0.1"
        }
        keep.id = dao.addProfile(keep).toInt()
        gone.id = dao.addProfile(gone).toInt()
        DreamDroid.setCurrentProfile(context, gone.id!!, true)

        val message = deleteConfirmedProfile(context, gone)

        assertEquals("Deleted profile 'f05-gone'", message)
        assertFalse(dao.getProfiles().any { it.id == gone.id })
        val currentId = DreamDroid.getCurrentProfile().id
        assertTrue(currentId != gone.id)
        assertTrue(dao.getProfiles().any { it.id == currentId })
        val prefId = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(DreamDroid.CURRENT_PROFILE, -1)
        assertTrue(prefId != gone.id)
        assertEquals(currentId, prefId)
    }
}
