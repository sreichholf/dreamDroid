package net.reichholf.dreamdroid.ui.epg

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [EpgDetailModalSheet] on the phone emulator turned to landscape (compact height). The sheet
 * measures against the real window, which `WithWindowSize` cannot shrink.
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
    fun theSheetKeepsTheTitleAndReachesEveryAction() {
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
        composeRule.onNodeWithText("Record series").performScrollTo().assertIsDisplayed()
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
