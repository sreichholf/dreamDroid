package net.reichholf.dreamdroid.ui.session

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerScreen
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.zap.ZapScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnlineOnlySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceOfflineNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
        SessionConnectionHolder.shared.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Connect),
            hasCache = true
        )
    }

    @After
    fun clearSession() {
        SessionConnectionHolder.shared.onFailure(EnigmaFailure.Unknown(null), hasCache = false)
    }

    @Test
    fun greyedPowerKeepsClickAndAnnouncesNeedsReceiver() {
        var clicked = 0
        composeRule.setContent {
            DreamDroidTheme {
                DrawerScreen(
                    state = DrawerListState(),
                    boxActionsBlocked = true,
                    onItemClick = { clicked = it }
                )
            }
        }
        val power = composeRule.onNodeWithText("Power Control")
        power.assert(hasStateDescription("Needs the receiver"))
        power.performClick()
        composeRule.waitForIdle()
        assertEquals(R.id.menu_navigation_power, clicked)
    }

    @Test
    fun greyedZapKeepsClickAndAnnouncesNeedsReceiver() {
        var clicked = 0
        val service = Service("1:0:1:0:0:0:0:0:0:0", "News")
        composeRule.setContent {
            DreamDroidTheme {
                ZapScreen(
                    items = listOf(service),
                    onItemClick = { clicked += 1 },
                    onItemLongClick = {}
                )
            }
        }
        val card = composeRule.onNodeWithText("News")
        card.assert(hasStateDescription("Needs the receiver"))
        card.performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicked)
    }

    @Test
    fun greyedSetTimerKeepsClickAndAnnouncesNeedsReceiver() {
        var clicked = 0
        composeRule.setContent {
            DreamDroidTheme {
                EpgDetailScreen(
                    content = EpgDetailContent(
                        title = "Evening news",
                        serviceName = "News",
                        description = "",
                        descriptionExtended = "",
                        dateLine = "20:00 (30 min)",
                        isNext = false
                    ),
                    onSetTimer = { clicked += 1 },
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {},
                    bodyHeightCap = null
                )
            }
        }
        val setTimer = composeRule.onNodeWithText("Set Timer")
        setTimer.assert(hasStateDescription("Needs the receiver"))
        setTimer.performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicked)
    }
}
