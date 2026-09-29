package net.reichholf.dreamdroid.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.ui.dialogs.MUTATION_PROGRESS_TAG
import net.reichholf.dreamdroid.ui.multiepg.MultiEpgScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2.1g-ii-d: EPG detail hosts as Material 3 [EpgDetailModalSheet] in composition
 * (no View [com.google.android.material.bottomsheet.BottomSheetDialog]).
 */
class EpgDetailDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun contentColorIsOnSurfaceInsideNightModalSheet() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        var setTimerClicks = 0
        val content = EpgDetailContent(
            title = "Tagesschau",
            serviceName = "Das Erste HD",
            description = "News",
            descriptionExtended = "Die Nachrichten um 20 Uhr.",
            dateLine = "20:00 (15 min)",
            isNext = false
        )
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                EpgDetailModalSheet(
                    content = content,
                    onDismiss = {},
                    onSetTimer = { setTimerClicks++ },
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertIsDisplayed().performClick()
        assertEquals(1, setTimerClicks)
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun tvFullscreenHidesActions() {
        val content = EpgDetailContent(
            title = "Tagesschau",
            serviceName = "Das Erste HD",
            description = "News",
            descriptionExtended = "Die Nachrichten um 20 Uhr.",
            dateLine = "20:00 (15 min)",
            isNext = false
        )
        composeRule.setContent {
            DreamDroidTheme {
                EpgDetailScreen(
                    content = content,
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {},
                    showActions = false,
                    bodyHeightCap = null
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertDoesNotExist()
        composeRule.onNodeWithText("Edit Timer").assertDoesNotExist()
        composeRule.onNodeWithTag(EPG_DETAIL_UNCAPPED_TAG).assertExists()
        composeRule.onNodeWithTag(EPG_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun sheetHostShowsSavingProgress() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = EpgEventDetailUiState(event = tagesschau(), saving = true),
                    onDismiss = {},
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.saving))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertIsDisplayed()
    }

    @Test
    fun savingProgressRemainsAfterSheetDismissWithoutDialog() {
        var state by mutableStateOf(EpgEventDetailUiState(event = tagesschau(), saving = true))
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = state,
                    onDismiss = { state = state.copy(event = null) },
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state = state.copy(event = null) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.saving))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun sheetHostKeepsUnavailableTitleInsteadOfDismissing() {
        val event = Event(
            title = "",
            serviceName = "Das Erste HD",
            startReadable = "20:00",
            durationReadable = "15"
        )
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = EpgEventDetailUiState(event = event),
                    onDismiss = {},
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        composeRule.waitForIdle()
        val unavailable = composeRule.activity.getString(R.string.not_available)
        composeRule.onNodeWithText(unavailable).assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertIsDisplayed()
    }

    @Test
    fun blockedTimerWritesLookOnlineOnlyInTheSheet() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = EpgEventDetailUiState(event = tagesschau(), timerWritesBlocked = true),
                    onDismiss = {},
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        val needsReceiver = composeRule.activity.getString(R.string.session_needs_receiver)
        composeRule.onNodeWithText("Set Timer").assert(hasStateDescription(needsReceiver))
        composeRule.onNodeWithText("Edit Timer").assert(hasStateDescription(needsReceiver))
        composeRule.onNodeWithText("IMDb").assert(!hasStateDescription(needsReceiver))
    }

    @Test
    fun sheetActionsReceiveTheShownEventAndDismiss() {
        var state by mutableStateOf(EpgEventDetailUiState(event = tagesschau()))
        var similar: Event? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = state,
                    onDismiss = { state = state.copy(event = null) },
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = { similar = it }
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.similar))
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(tagesschau(), similar)
            assertEquals(null, state.event)
        }
        composeRule.onNodeWithText("Tagesschau").assertDoesNotExist()
    }

    @Test
    fun multiEpgBarOpensSharedEpgDetailSheet() {
        val start = 1_700_000_000L
        var state by mutableStateOf(EpgEventDetailUiState())
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            description = "News",
                            descriptionExtended = "Die Nachrichten um 20 Uhr.",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD"
                        ),
                        startSec = start,
                        endSec = start + 1800
                    )
                )
            )
        )
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = { state = EpgEventDetailUiState(event = it) }
                )
                EpgEventDetailSheet(
                    state = state,
                    onDismiss = {},
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {}
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
    }

    private fun tagesschau() = Event(
        title = "Tagesschau",
        serviceName = "Das Erste HD",
        description = "News",
        descriptionExtended = "Die Nachrichten um 20 Uhr.",
        startReadable = "20:00",
        durationReadable = "15"
    )
}
