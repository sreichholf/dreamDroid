package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.autotimer.PreviewMatch
import net.reichholf.dreamdroid.enigma.autotimer.Verdict
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoTimerPreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun listsUpcomingAndSkippedWithCounts() {
        show(ready(upcoming = listOf(UPCOMING), skipped = listOf(SKIPPED)))

        composeRule.onNodeWithText("Upcoming (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Wilsberg - In Treu und Glauben").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Wilsberg - Einfach weg").assertIsDisplayed()
        composeRule.onNodeWithText(SKIPPED.log).assertDoesNotExist()
    }

    @Test
    fun aTapOnASkippedEventAsksForItsLog() {
        val toggled = mutableListOf<PreviewMatch>()
        show(ready(skipped = listOf(SKIPPED)), onToggleLog = { toggled += it })

        composeRule.onNodeWithText("Wilsberg - Einfach weg").performClick()

        composeRule.runOnIdle { assertEquals(listOf(SKIPPED), toggled) }
    }

    @Test
    fun aTapOnAnUpcomingEventOpensIt() {
        val opened = mutableListOf<PreviewMatch>()
        show(ready(upcoming = listOf(UPCOMING)), onOpenMatch = { opened += it })

        composeRule.onNodeWithText(UPCOMING.title).performClick()

        composeRule.runOnIdle { assertEquals(listOf(UPCOMING), opened) }
    }

    @Test
    fun theSheetShowsTheProgrammeWithoutActions() {
        val event = Event(
            title = UPCOMING.title,
            start = UPCOMING.begin.epochSecond.toString(),
            duration = "6000",
            description = "Krimi",
            descriptionExtended = "Georg Wilsberg ermittelt.",
            serviceReference = UPCOMING.serviceRef,
            serviceName = UPCOMING.serviceName
        )
        show(
            ready(upcoming = listOf(UPCOMING)).copy(
                detail = AutoTimerMatchDetail(UPCOMING, MatchEpg.Found(event))
            )
        )

        composeRule.onNodeWithText("Georg Wilsberg ermittelt.").assertIsDisplayed()
        composeRule.onNodeWithText("Krimi").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertDoesNotExist()
        composeRule.onNodeWithText("Similar").assertDoesNotExist()
    }

    @Test
    fun theSheetSaysWhenTheEpgHasNoDetails() {
        show(
            ready(upcoming = listOf(UPCOMING)).copy(
                detail = AutoTimerMatchDetail(UPCOMING, MatchEpg.Missing)
            )
        )

        composeRule.onNodeWithText("The receiver's EPG has no details for this event.")
            .assertIsDisplayed()
    }

    @Test
    fun aSkippedEventWithoutAReasonOpensIt() {
        val opened = mutableListOf<PreviewMatch>()
        val toggled = mutableListOf<PreviewMatch>()
        val silent = SKIPPED.copy(log = "")
        show(
            ready(skipped = listOf(silent)),
            onToggleLog = { toggled += it },
            onOpenMatch = { opened += it }
        )

        composeRule.onNodeWithText(silent.title).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(silent), opened)
            assertEquals(emptyList<PreviewMatch>(), toggled)
        }
    }

    @Test
    fun aSkippedEventSaysWhetherItsReasonShows() {
        show(ready(skipped = listOf(SKIPPED)))

        composeRule.onNode(hasText(SKIPPED.title, substring = true) and hasClickAction())
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reason hidden")
            )
    }

    @Test
    fun anExpandedSkippedEventShowsItsLog() {
        show(ready(skipped = listOf(SKIPPED)).copy(expanded = setOf(SKIPPED.key)))

        composeRule.onNodeWithText(SKIPPED.log).assertIsDisplayed()
    }

    @Test
    fun noMatchesSaysSo() {
        show(ready())

        composeRule.onNodeWithText("Upcoming (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing in the EPG matches right now.").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped (0)").assertDoesNotExist()
    }

    @Test
    fun aDisabledAutoTimerOffersEnable() {
        var enables = 0
        show(state(AutoTimerPreviewContent.Disabled), onEnable = { enables++ })

        composeRule.onNodeWithText("The receiver previews enabled AutoTimers only.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Enable").performClick()

        composeRule.runOnIdle { assertEquals(1, enables) }
    }

    @Test
    fun enableIsOffWhileItRuns() {
        show(state(AutoTimerPreviewContent.Disabled).copy(pending = true))

        composeRule.onNodeWithText("Enable").assertIsNotEnabled()
    }

    @Test
    fun aPluginFailureShowsItsText() {
        show(state(AutoTimerPreviewContent.PluginFailed("list index out of range")))

        composeRule.onNodeWithText("AutoTimer failed: list index out of range").assertIsDisplayed()
    }

    @Test
    fun aRenumberedAutoTimerIsGone() {
        show(state(AutoTimerPreviewContent.Gone))

        composeRule.onNodeWithText(
            "This AutoTimer changed on the receiver. Go back to the list to see it as it is now."
        ).assertIsDisplayed()
    }

    private fun show(
        state: AutoTimerPreviewUiState,
        onEnable: () -> Unit = {},
        onToggleLog: (PreviewMatch) -> Unit = {},
        onOpenMatch: (PreviewMatch) -> Unit = {}
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                AutoTimerPreviewScreen(
                    state = state,
                    onRefresh = {},
                    onEnable = onEnable,
                    onToggleLog = onToggleLog,
                    onOpenMatch = onOpenMatch,
                    onDismissMatch = {}
                )
            }
        }
    }

    private fun state(content: AutoTimerPreviewContent) =
        AutoTimerPreviewUiState(name = "Wilsberg", content = content)

    private fun ready(
        upcoming: List<PreviewMatch> = emptyList(),
        skipped: List<PreviewMatch> = emptyList()
    ) = state(AutoTimerPreviewContent.Ready(upcoming, skipped))

    private companion object {
        val UPCOMING = PreviewMatch(
            serviceRef = "1:0:19:2B7A:3F3:1:C00000:0:0:0:",
            serviceName = "zdf_neo HD",
            title = "Wilsberg - In Treu und Glauben",
            begin = Instant.ofEpochSecond(1790791800),
            end = Instant.ofEpochSecond(1790797800),
            autoTimerName = "Wilsberg",
            verdict = Verdict.Record,
            log = "possible epgmatch Wilsberg - In Treu und Glauben"
        )

        val SKIPPED = UPCOMING.copy(
            serviceRef = "1:0:19:2B66:3F3:1:C00000:0:0:0:",
            serviceName = "ZDF HD",
            title = "Wilsberg - Einfach weg",
            begin = Instant.ofEpochSecond(1791051000),
            verdict = Verdict.Skip,
            log = "Skipping an event because of timespan check"
        )
    }
}
