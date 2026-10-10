package net.reichholf.dreamdroid.ui.epg

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.window.core.layout.WindowSizeClass
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.compose.showsListDetailPanes
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The phone emulator turned to landscape: expanded wide, compact tall, as a current phone. The pane
 * rule and [EpgDetailModalSheet] see the real window here, which `WithWindowSize` only fakes for
 * the size class and cannot shrink for a sheet.
 */
class EpgDetailLandscapeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun turnToLandscape() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(ROTATION_TIMEOUT_MS) {
            composeRule.activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        }
    }

    @After
    fun releaseOrientation() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun aPhoneInLandscapeShowsOnePane() {
        var expandedWide = false
        var twoPanes = true
        composeRule.setContent {
            expandedWide = currentWindowAdaptiveInfoV2().windowSizeClass
                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
            twoPanes = showsListDetailPanes()
        }
        composeRule.waitForIdle()

        // Material 3 alone would give this window two panes; the CI phone must be that wide.
        assertTrue("landscape window is expanded wide (CI AVD profile)", expandedWide)
        assertFalse(twoPanes)
    }

    @Test
    fun theSheetKeepsTheTitleAndShowsEveryAction() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventDetailSheet(
                    state = EpgEventDetailUiState(event = longEvent(), autoTimerAvailable = true),
                    onDismiss = {},
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {},
                    onRecordSeries = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("IMDb").assertIsDisplayed()
        // The sheet (~387dp on the CI Pixel 7) pins the actions and lets the text yield; before,
        // the text kept its 360dp cap and pushed the second row of actions out of the sheet.
        composeRule.onNodeWithText("Set Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Record series").assertIsDisplayed()
    }

    /** More text than the sheet's 360dp body cap, as a long film description has. */
    private fun longEvent() = Event(
        title = "Tagesschau",
        serviceName = "Das Erste HD",
        description = "News",
        descriptionExtended = List(40) { "Die Nachrichten um 20 Uhr." }.joinToString("\n"),
        startReadable = "20:00",
        durationReadable = "15"
    )

    private companion object {
        const val ROTATION_TIMEOUT_MS = 10_000L
    }
}
