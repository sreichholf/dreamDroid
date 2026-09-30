package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.ClockWindow
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.Extras
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoTimerEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = RecordingActions()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsTheSearchTheChannelsAndTheWindow() {
        show(editing(DRAFT))

        composeRule.onNodeWithText("Wilsberg").assertIsDisplayed()
        composeRule.onNodeWithText("Title contains").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF HD").assertIsDisplayed()
        composeRule.onNodeWithText("Favourites (TV)").assertIsDisplayed()
        val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        composeRule.onNodeWithText(LocalTime.of(20, 0).format(format)).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(LocalTime.of(23, 0).format(format)).assertIsDisplayed()
    }

    @Test
    fun aBlankMatchShowsItsError() {
        show(editing(DRAFT).copy(matchError = UiText.Raw("Enter what to search for.")), match = "")

        composeRule.onNodeWithText("Enter what to search for.").assertIsDisplayed()
    }

    @Test
    fun tappingAChannelRemovesIt() {
        show(editing(DRAFT))

        composeRule.onNodeWithContentDescription("Remove ZDF HD").performClick()

        composeRule.runOnIdle { assertEquals(listOf("removeTarget ZDF HD"), actions.calls) }
    }

    @Test
    fun aDayChipTogglesTheDay() {
        show(editing(DRAFT))
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())

        composeRule.onNodeWithText(sunday).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("toggleDay ${DayFilter.On(DayOfWeek.SUNDAY)}"), actions.calls)
        }
    }

    @Test
    fun channelsWithACommaAreLocked() {
        val locked = DRAFT.copy(
            targets = listOf(Target.Channel("4097:0:1:0:0:0:0:0:0:0:a,b:", "IPTV"))
        )
        show(editing(locked))

        composeRule.onNodeWithText(
            "A channel reference contains a comma, so dreamDroid cannot change the channels " +
                "of this AutoTimer."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Add channels").assertDoesNotExist()
    }

    @Test
    fun settingsDreamDroidDoesNotEditAreNamed() {
        show(
            editing(DRAFT).copy(
                loaded = AutoTimer(AutoTimerId(1), DRAFT, Extras(counter = true, vps = true))
            )
        )

        composeRule.onNodeWithText(
            "Also uses a counter, VPS. dreamDroid keeps them as they are."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aChangeOnTheReceiverOffersAReload() {
        show(AutoTimerEditUiState(isCreate = false, content = AutoTimerEditContent.Changed))

        composeRule.onNodeWithText("Reload").performClick()

        composeRule.runOnIdle { assertEquals(listOf("reload"), actions.calls) }
    }

    private fun show(state: AutoTimerEditUiState, match: String = "Wilsberg") {
        composeRule.setContent {
            DreamDroidTheme {
                AutoTimerEditScreen(
                    state = state,
                    match = TextFieldState(match),
                    name = TextFieldState(""),
                    actions = actions,
                    onPickTargets = {}
                )
            }
        }
    }

    private fun editing(draft: AutoTimerSettings) = AutoTimerEditUiState(
        isCreate = false,
        content = AutoTimerEditContent.Editing,
        base = draft,
        draft = draft
    )

    private class RecordingActions : AutoTimerEditActions {
        val calls = mutableListOf<String>()

        override fun setSearchType(type: SearchType) {
            calls += "setSearchType $type"
        }

        override fun setCaseSensitive(sensitive: Boolean) {
            calls += "setCaseSensitive $sensitive"
        }

        override fun setEnabled(enabled: Boolean) {
            calls += "setEnabled $enabled"
        }

        override fun setZap(zap: Boolean) {
            calls += "setZap $zap"
        }

        override fun removeTarget(target: Target) {
            calls += "removeTarget ${target.name}"
        }

        override fun setTimeWindow(on: Boolean) {
            calls += "setTimeWindow $on"
        }

        override fun toggleDay(day: DayFilter) {
            calls += "toggleDay $day"
        }

        override fun setDateWindow(on: Boolean) {
            calls += "setDateWindow $on"
        }

        override fun openPicker(pick: AutoTimerEditPick) {
            calls += "openPicker $pick"
        }

        override fun dismissPicker() {
            calls += "dismissPicker"
        }

        override fun onTimePicked(hour: Int, minute: Int) {
            calls += "onTimePicked $hour:$minute"
        }

        override fun onDatePicked(utcDateMillis: Long) {
            calls += "onDatePicked $utcDateMillis"
        }

        override fun reload() {
            calls += "reload"
        }
    }

    private companion object {
        val DRAFT = AutoTimerSettings.NEW.copy(
            match = "Wilsberg",
            name = "Wilsberg",
            targets = listOf(
                Target.Channel("1:0:19:2B66:3F3:1:C00000:0:0:0:", "ZDF HD"),
                Target.Bouquet("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", "Favourites (TV)")
            ),
            timeWindow = ClockWindow(LocalTime.of(20, 0), LocalTime.of(23, 0))
        )
    }
}
