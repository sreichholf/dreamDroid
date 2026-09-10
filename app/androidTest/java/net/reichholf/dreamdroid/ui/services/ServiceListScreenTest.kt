package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsChannelAndNowNext() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL,
                            nowTitle = "Tagesschau",
                            nowStart = "20:00",
                            nowDuration = "15",
                            nextTitle = "Wetter",
                            nextStart = "20:15",
                            nextDuration = "5",
                            progressMax = 15,
                            progress = 3,
                        ),
                    ),
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00  Tagesschau  15").assertIsDisplayed()
        composeRule.onNodeWithText("20:15  Wetter  5").assertIsDisplayed()
    }

    @Test
    fun withoutPiconsChannelTitleSitsAtLeadingEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()

        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ZDF",
                            kind = ServiceRowKind.CHANNEL,
                        ),
                    ),
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> },
                )
            }
        }
        // Card padding 12dp + list horizontal padding 8dp = 20dp from root.
        // useUnmergedTree: combinedClickable merges semantics up to the Card (left=8dp).
        composeRule.onNodeWithText("ZDF", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(20.dp)
    }

    @Test
    fun channelTapReportsWindowPositionOfRowNotOrigin() {
        var tapX = -1
        var tapY = -1
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL,
                        ),
                        ServiceListItem(
                            index = 1,
                            reference = "1:0:1:2:1:1:1:0:0:0:",
                            name = "ZDF",
                            kind = ServiceRowKind.CHANNEL,
                        ),
                    ),
                    onItemClick = { _, x, y ->
                        tapX = x
                        tapY = y
                    },
                    onItemLongClick = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("ZDF").performClick()
        composeRule.waitForIdle()
        // Second row must not report the fragment-root origin (0,0) used by the old PopupMenu bug.
        assertTrue("expected tapX >= 0, got $tapX", tapX >= 0)
        assertTrue("expected second-row tapY > 0, got $tapY", tapY > 0)
    }
}
