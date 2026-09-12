package net.reichholf.dreamdroid.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
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
            InstrumentationRegistry.getInstrumentation().targetContext,
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
            isNext = false,
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
                    onSimilar = {},
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
                onSurface.luminance() > 0.5f,
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
            isNext = false,
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
                    bodyHeightCap = null,
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertDoesNotExist()
        composeRule.onNodeWithText("Edit Timer").assertDoesNotExist()
    }

    @Test
    fun sheetHostShowsSavingProgress() {
        val session = EpgEventDialogSession()
        session.showDetail(
            Event(
                title = "Tagesschau",
                serviceName = "Das Erste HD",
                description = "News",
                descriptionExtended = "Die Nachrichten um 20 Uhr.",
                startReadable = "20:00",
                durationReadable = "15",
            ),
        )
        session.progress = IndeterminateProgressState(message = "Saving")
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheetHost(session)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Saving").assertIsDisplayed()
    }
}
