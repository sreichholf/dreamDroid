package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.autotimer.AfterEvent
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.ClockWindow
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.DuplicateCheck
import net.reichholf.dreamdroid.enigma.autotimer.Extras
import net.reichholf.dreamdroid.enigma.autotimer.Filters
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoTimerListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun rowsSummariseWhatWhereAndWhen() {
        show(ready(WILSBERG, PAUSED))

        composeRule.onNodeWithText("Wilsberg").assertIsDisplayed()
        composeRule.onNodeWithText("“Wilsberg” · ZDF HD, zdf_neo HD +1")
            .assertIsDisplayed()
        val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val window = "${LocalTime.of(20, 0).format(format)}–" +
            LocalTime.of(23, 0).format(format)
        val days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        composeRule.onNodeWithText("$window · $days").assertIsDisplayed()

        composeRule.onNodeWithText("“Tatort” · All channels").assertIsDisplayed()
        composeRule.onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test
    fun aTapOpensTheAutoTimer() {
        val opened = mutableListOf<AutoTimerEntry.Readable>()
        show(ready(WILSBERG), onOpen = { opened += it })

        composeRule.onNodeWithText("Wilsberg").performClick()

        composeRule.runOnIdle { assertEquals(listOf(WILSBERG), opened) }
    }

    @Test
    fun anUnreadableEntryIsNamedAndExplained() {
        show(ready(AutoTimerEntry.Unreadable(AutoTimerId(5), "Odd", "afterevent: hibernate")))

        composeRule.onNodeWithText("Odd").assertIsDisplayed()
        composeRule.onNodeWithText("dreamDroid cannot read this AutoTimer.").assertIsDisplayed()
    }

    @Test
    fun emptyListSaysSo() {
        show(ready())

        composeRule.onNodeWithText("No AutoTimers yet.").assertIsDisplayed()
    }

    @Test
    fun missingPluginExplainsWhyAndOffersAReload() {
        var reloads = 0
        show(
            AutoTimerListUiState(content = AutoTimerListContent.PluginMissing),
            onRefresh = { reloads++ }
        )

        composeRule.onNodeWithText(
            "AutoTimer needs the AutoTimer plugin on the receiver (package " +
                "enigma2-plugin-extensions-autotimer). This receiver does not have it " +
                "installed. Install it on the receiver, then tap Reload."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Reload").performClick()

        composeRule.runOnIdle { assertEquals(1, reloads) }
    }

    @Test
    fun failureShowsItsMessage() {
        show(AutoTimerListUiState(content = AutoTimerListContent.Failed(UiText.Raw("Nope"))))

        composeRule.onNodeWithText("Nope").assertIsDisplayed()
    }

    @Test
    fun theSwitchReportsTheNewState() {
        val changes = mutableListOf<Pair<AutoTimerEntry.Readable, Boolean>>()
        show(ready(WILSBERG), onEnabledChange = { entry, on -> changes += entry to on })

        composeRule.onNode(isToggleable()).assertIsOn().performClick()

        composeRule.runOnIdle { assertEquals(listOf(WILSBERG to false), changes) }
    }

    @Test
    fun aRunningWriteDisablesTheRowControls() {
        show(ready(WILSBERG).copy(pending = true))

        composeRule.onNode(isToggleable()).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("More options").assertIsNotEnabled()
    }

    @Test
    fun theRowMenuOffersDelete() {
        val actions = mutableListOf<Pair<AutoTimerEntry, AutoTimerRowAction>>()
        val menus = mutableListOf<AutoTimerEntry>()
        show(
            ready(WILSBERG).copy(
                menu = RowMenuState(WILSBERG.id.value, AutoTimerRowAction.entries)
            ),
            onMenu = { menus += it },
            onMenuAction = { entry, action -> actions += entry to action }
        )

        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(WILSBERG to AutoTimerRowAction.Delete), actions)
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.runOnIdle { assertEquals(listOf(WILSBERG), menus) }
    }

    @Test
    fun deleteAsksWithTheName() {
        var confirmed = 0
        composeRule.setContent {
            DreamDroidTheme {
                AutoTimerListDialogs(
                    state = ready(WILSBERG).copy(deleting = WILSBERG),
                    onConfirmDelete = { confirmed++ },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Delete the AutoTimer \"Wilsberg\"? Timers it already added stay."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertEquals(1, confirmed) }
    }

    private fun show(
        state: AutoTimerListUiState,
        onRefresh: () -> Unit = {},
        onOpen: (AutoTimerEntry.Readable) -> Unit = {},
        onEnabledChange: (AutoTimerEntry.Readable, Boolean) -> Unit = { _, _ -> },
        onMenu: (AutoTimerEntry) -> Unit = {},
        onMenuAction: (AutoTimerEntry, AutoTimerRowAction) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                AutoTimerListScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onOpen = onOpen,
                    onEnabledChange = onEnabledChange,
                    onMenu = onMenu,
                    onMenuAction = onMenuAction,
                    onMenuDismiss = {}
                )
            }
        }
    }

    private fun ready(vararg entries: AutoTimerEntry) =
        AutoTimerListUiState(content = AutoTimerListContent.Ready(entries.toList()))

    private companion object {
        val SETTINGS = AutoTimerSettings(
            name = "Wilsberg",
            match = "Wilsberg",
            enabled = true,
            searchType = SearchType.Partial,
            caseSensitive = false,
            targets = listOf(
                Target.Channel("1:0:19:2B66:3F3:1:C00000:0:0:0:", "ZDF HD"),
                Target.Channel("1:0:19:2B7A:3F3:1:C00000:0:0:0:", "zdf_neo HD"),
                Target.Bouquet("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", "Favourites (TV)")
            ),
            timeWindow = ClockWindow(LocalTime.of(20, 0), LocalTime.of(23, 0)),
            dateWindow = null,
            offset = null,
            maxDurationMinutes = null,
            location = null,
            tags = emptyList(),
            include = Filters(
                days = listOf(DayFilter.On(DayOfWeek.SATURDAY), DayFilter.On(DayOfWeek.SUNDAY))
            ),
            exclude = Filters(),
            afterEvent = AfterEvent.ReceiverDefault,
            recordMode = RecordMode.Record,
            duplicates = DuplicateCheck.Off
        )

        val WILSBERG = AutoTimerEntry.Readable(AutoTimer(AutoTimerId(1), SETTINGS, Extras()))

        val PAUSED = AutoTimerEntry.Readable(
            AutoTimer(
                AutoTimerId(2),
                SETTINGS.copy(
                    name = "",
                    match = "Tatort",
                    enabled = false,
                    targets = emptyList(),
                    timeWindow = null,
                    include = Filters()
                ),
                Extras()
            )
        )
    }
}
