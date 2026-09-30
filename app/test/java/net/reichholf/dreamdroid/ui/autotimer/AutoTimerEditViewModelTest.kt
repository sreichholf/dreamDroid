package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.autotimer.AfterEvent
import net.reichholf.dreamdroid.enigma.autotimer.AfterEventAction
import net.reichholf.dreamdroid.enigma.autotimer.ClockWindow
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.DescriptionCompare
import net.reichholf.dreamdroid.enigma.autotimer.DuplicateScope
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [AutoTimerEditViewModel] over the real repository and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTimerEditViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val autoTimers =
        AutoTimerRepository(enigmaClients(receiver.repository), receiver.repository)
    private val timers = receiver.timerRepository()
    private val viewModels = mutableListOf<AutoTimerEditViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))
        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        receiver.respond(EDIT, simpleResult(true, "AutoTimer wurde erfolgreich geändert"))
        receiver.respond(
            LOCATIONS,
            "<e2locations><e2location>/media/hdd/movie/</e2location>" +
                "<e2location>/media/hdd/series/</e2location></e2locations>"
        )
        receiver.respond(TAGS, "<e2tags><e2tag>Krimi</e2tag><e2tag>News</e2tag></e2tags>")
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun editLoadsTheAutoTimerAndSendsOnlyWhatChanged() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        assertEquals("Wilsberg", viewModel.match.text)
        assertEquals("dreamDroid test Wilsberg", viewModel.name.text)

        viewModel.toggleDay(DayFilter.On(DayOfWeek.SATURDAY))
        viewModel.save()
        val saved = viewModel.saved()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), saved)
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals(
            listOf("id", "match", "name", "title", "shortdescription", "description", "dayofweek"),
            query.queryParameterNames.toList()
        )
        assertEquals(listOf("6"), query.queryParameterValues("dayofweek"))
    }

    @Test
    fun anUnchangedEditShowsTheAutoTimerWithoutWriting() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()

        viewModel.save()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), viewModel.saved())
        assertTrue(receiver.requestsTo(EDIT).isEmpty())
    }

    @Test
    fun createStartsFromTheDefaultsAndFindsTheNewAutoTimer() = runBlocking<Unit> {
        val viewModel = create()
        viewModel.editing()
        assertEquals("", viewModel.match.text)
        receiver.respond(EDIT, loadWebFixture("autotimer/result_add.xml"))

        viewModel.save()
        assertEquals(
            UiText.Resource(R.string.autotimer_match_empty),
            viewModel.uiState.value.matchError
        )
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        viewModel.match.set("Wilsberg")
        viewModel.name.set(" dreamDroid test Wilsberg ")
        viewModel.setSearchType(SearchType.Exact)
        viewModel.save()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), viewModel.saved())
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertNull(query.queryParameter("id"))
        assertEquals("dreamDroid test Wilsberg", query.queryParameter("name"))
        assertEquals("exact", query.queryParameter("searchType"))
    }

    @Test
    fun anIdNamingAnotherAutoTimerIsGone() = runBlocking<Unit> {
        val viewModel = edit(name = "Tatort")

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerEditContent.Gone }
        }
    }

    @Test
    fun aRenumberedListStopsTheSaveAndReloadStartsOver() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        viewModel.setEnabled(true)
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_enabled.xml").replace("id=\"2\"", "id=\"1\"")
        )

        viewModel.save()
        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerEditContent.Changed }
        }
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        receiver.respond(LIST, loadWebFixture("autotimer/list_disabled_full.xml"))
        viewModel.reload()
        viewModel.editing()
        assertEquals(false, viewModel.uiState.value.draft.enabled)
    }

    @Test
    fun pickedTargetsAreAddedOnce() = runBlocking<Unit> {
        val viewModel = edit()
        val before = viewModel.editing().draft.targets
        val zdf = before.first()
        val extra = Target.Channel("1:0:19:283D:3FB:1:C00000:0:0:0:", "Das Erste HD")

        viewModel.addTargets(listOf(zdf, extra))

        assertEquals(before + extra, viewModel.uiState.value.draft.targets)
    }

    @Test
    fun zappingAgainKeepsTheLoadedEndTimeSetting() = runBlocking<Unit> {
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml")
                .replace("enabled=\"no\"", "enabled=\"no\" justplay=\"1\" setEndtime=\"0\"")
        )
        val viewModel = edit()
        viewModel.editing()

        viewModel.setZap(false)
        viewModel.setZap(true)

        assertEquals(RecordMode.Zap(setEndTime = false), viewModel.uiState.value.draft.recordMode)
    }

    @Test
    fun filtersAreAddedToTheChosenListAndRemoved() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()

        viewModel.setFilterKind(FilterKind.ExcludeTitle)
        viewModel.filterText.set(" Wiederholung ")
        viewModel.addFilter()
        viewModel.removeFilter(FilterKind.ExcludeTitle, "Vorschau")

        assertEquals(listOf("Wiederholung"), viewModel.uiState.value.draft.exclude.title)
        assertEquals("", viewModel.filterText.text)
        viewModel.save()
        viewModel.saved()
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals(listOf("Wiederholung"), query.queryParameterValues("!title"))
        assertEquals(listOf("Wiederholung"), query.queryParameterValues("!description"))
    }

    @Test
    fun marginsAndLengthAreReadFromTheirFieldsOnSave() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        assertEquals("5", viewModel.offsetBefore.text)
        assertEquals("10", viewModel.offsetAfter.text)
        assertEquals("120", viewModel.maxDuration.text)

        viewModel.offsetAfter.set("x")
        viewModel.save()
        assertEquals(
            UiText.Resource(R.string.autotimer_minutes_invalid),
            viewModel.uiState.value.offsetError
        )
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        viewModel.offsetAfter.set("15")
        viewModel.maxDuration.set("90")
        viewModel.save()
        viewModel.saved()

        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals("5,15", query.queryParameter("offset"))
        assertEquals("90", query.queryParameter("maxduration"))
    }

    @Test
    fun turningMarginsOffClearsThemOnTheBox() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()

        viewModel.setOffset(false)
        viewModel.setMaxDuration(false)
        viewModel.save()
        viewModel.saved()

        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals("", query.queryParameter("offset"))
        assertEquals("", query.queryParameter("maxduration"))
    }

    @Test
    fun recordingChoicesComeFromTheReceiver() = runBlocking<Unit> {
        val viewModel = edit()
        val state = withTimeout(TIMEOUT) {
            viewModel.uiState.first {
                it.content == AutoTimerEditContent.Editing &&
                    it.locations.size == 2
            }
        }
        assertEquals(listOf("Krimi", "News"), state.tagChoices)

        viewModel.setLocation("/media/hdd/series/")
        viewModel.onTagsPicked(listOf("Krimi"))
        viewModel.setAfterEvent(AfterEventAction.DeepStandby)
        viewModel.setDuplicateScope(DuplicateScope.AnyService)
        viewModel.setDuplicateCompare(DescriptionCompare.Title)
        viewModel.save()
        viewModel.saved()

        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals("/media/hdd/series/", query.queryParameter("location"))
        assertEquals(listOf("Krimi"), query.queryParameterValues("tag"))
        assertEquals("deepstandby", query.queryParameter("afterevent"))
        assertEquals("2", query.queryParameter("avoidDuplicateDescription"))
        assertEquals("0", query.queryParameter("searchForDuplicateDescription"))
    }

    @Test
    fun anotherAfterEventActionKeepsItsTimeWindow() = runBlocking<Unit> {
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml").replace(
                "<afterevent>standby</afterevent>",
                "<afterevent from=\"22:00\" to=\"06:00\">standby</afterevent>"
            )
        )
        val viewModel = edit()
        viewModel.editing()

        viewModel.setAfterEvent(AfterEventAction.Auto)

        assertEquals(
            AfterEvent.Fixed(
                AfterEventAction.Auto,
                ClockWindow(LocalTime.of(22, 0), LocalTime.of(6, 0))
            ),
            viewModel.uiState.value.draft.afterEvent
        )
    }

    @Test
    fun recordSeriesRouteCarriesTheEvent() {
        val route = AutoTimerEdit.recordSeries(
            Event(
                title = "Wilsberg - Einfach weg",
                start = "1791051000",
                duration = "6000",
                serviceReference = ZDF,
                serviceName = "ZDF HD"
            )
        )

        assertEquals(
            AutoTimerEdit(
                title = "Wilsberg - Einfach weg",
                serviceRef = ZDF,
                serviceName = "ZDF HD",
                beginSec = 1791051000,
                durationSec = 6000
            ),
            route
        )
    }

    @Test
    fun recordSeriesSearchesTheTitleOnTheEventsChannel() = runBlocking<Unit> {
        receiver.respond(EDIT, loadWebFixture("autotimer/result_add.xml"))
        val viewModel = viewModel(
            SavedStateHandle(
                mapOf(
                    AutoTimerEdit::id.name to AutoTimerEditViewModel.NEW_ID,
                    AutoTimerEdit::name.name to "",
                    AutoTimerEdit::title.name to "Wilsberg",
                    AutoTimerEdit::serviceRef.name to ZDF,
                    AutoTimerEdit::serviceName.name to "ZDF HD",
                    AutoTimerEdit::beginSec.name to 1791051000L,
                    AutoTimerEdit::durationSec.name to 6000L
                )
            )
        )

        val state = viewModel.editing()

        assertEquals("Wilsberg", viewModel.match.text)
        assertEquals(listOf(Target.Channel(ZDF, "ZDF HD")), state.draft.targets)
        assertNull(state.draft.timeWindow)
        val zone = ZoneId.systemDefault()
        val suggested = ClockWindow(
            Instant.ofEpochSecond(1791051000L - 3600).atZone(zone).toLocalTime(),
            Instant.ofEpochSecond(1791051000L + 6000 + 3600).atZone(zone).toLocalTime()
        )
        assertEquals(suggested, state.suggestedWindow)

        // The box names an AutoTimer without a name after its match.
        receiver.respond(
            LIST,
            loadWebFixture("autotimer/list_disabled_full.xml")
                .replace("name=\"dreamDroid test Wilsberg\"", "name=\"Wilsberg\"")
        )
        viewModel.applySuggestedWindow()
        viewModel.save()
        assertEquals(AutoTimerPreview(1, "Wilsberg"), viewModel.saved())

        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertNull(query.queryParameter("id"))
        assertEquals("Wilsberg", query.queryParameter("match"))
        assertEquals(ZDF, query.queryParameter("services"))
        assertEquals(
            suggested.from.format(DateTimeFormatter.ofPattern("HH:mm")),
            query.queryParameter("timespanFrom")
        )
    }

    @Test
    fun processDeathKeepsTheDraftAndSavesItAsAnEdit() = runBlocking<Unit> {
        val handle = handle(id = 1, name = "dreamDroid test Wilsberg")
        val viewModel = viewModel(handle)
        viewModel.editing()
        viewModel.setCaseSensitive(true)
        viewModel.match.set("Wilsberg!")
        val lists = receiver.requestsTo(LIST).size
        viewModel.cancelAndJoin()

        // What a Bundle keeps: every value written and read back as Serializable.
        val restored = viewModel(throughBundle(handle))

        assertEquals(AutoTimerEditContent.Editing, restored.uiState.value.content)
        assertTrue(restored.uiState.value.draft.caseSensitive)
        assertEquals("Wilsberg!", restored.match.text)
        restored.save()
        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), restored.saved())
        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals("1", query.queryParameter("id"))
        assertEquals("Wilsberg!", query.queryParameter("match"))
        // The restore asked nothing: the only lists since are the save's guard and locate.
        assertEquals(lists + 2, receiver.requestsTo(LIST).size)
    }

    @Test
    fun aCreateTheBoxDoesNotListIsDoneWithoutASecondSave() = runBlocking<Unit> {
        val viewModel = create()
        viewModel.editing()
        receiver.respond(EDIT, loadWebFixture("autotimer/result_add.xml"))
        viewModel.match.set("Tatort")

        viewModel.save()
        val done = withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content is AutoTimerEditContent.Saved }
        }
        viewModel.save()

        assertEquals(
            AutoTimerEditContent.Saved(UiText.Raw("AutoTimer wurde erfolgreich hinzugefügt")),
            done.content
        )
        assertNull(done.saved)
        assertEquals(1, receiver.requestsTo(EDIT).size)
    }

    @Test
    fun anotherReceiverEndsTheFormUntilItsOwnIsBack() = runBlocking<Unit> {
        val viewModel = create()
        viewModel.editing()
        viewModel.match.set("Tatort")
        val home = receiver.repository.requireCurrent()

        receiver.repository.setCurrent(otherProfile(home))
        withTimeout(TIMEOUT) {
            viewModel.uiState.first { it.content == AutoTimerEditContent.OtherReceiver }
        }
        viewModel.save()
        assertTrue(receiver.requestsTo(EDIT).isEmpty())

        receiver.repository.setCurrent(home)
        viewModel.editing()
        assertEquals("Tatort", viewModel.match.text)
    }

    @Test
    fun aDateWindowThatEndsOnItsFirstDayIsNotSaved() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        viewModel.setDateWindow(true)
        val window = viewModel.uiState.value.draft.dateWindow!!
        viewModel.openPicker(AutoTimerEditPick.DateBefore)
        viewModel.onDatePicked(utcMillis(window.after))

        assertTrue(viewModel.uiState.value.dateWindowInvalid)
        viewModel.save()
        viewModel.setDateWindow(false)
        viewModel.save()

        assertEquals(AutoTimerPreview(1, "dreamDroid test Wilsberg"), viewModel.saved())
        assertTrue(receiver.requestsTo(EDIT).isEmpty())
    }

    @Test
    fun aPickedDayIsLocalMidnightInAnyZone() = runBlocking<Unit> {
        val zones = listOf("Pacific/Kiritimati", "America/Los_Angeles")
        val default = TimeZone.getDefault()
        try {
            zones.forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val viewModel = edit()
                viewModel.editing()
                viewModel.setDateWindow(true)
                val today = LocalDate.now(ZoneId.of(zone))
                assertEquals(
                    today.atStartOfDay(ZoneId.of(zone)).toInstant(),
                    viewModel.uiState.value.draft.dateWindow?.after
                )

                val day = LocalDate.of(2026, 10, 3)
                viewModel.openPicker(AutoTimerEditPick.DateAfter)
                // The Material date picker hands over midnight UTC of the picked day.
                viewModel.onDatePicked(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

                assertEquals(
                    day.atStartOfDay(ZoneId.of(zone)).toInstant(),
                    viewModel.uiState.value.draft.dateWindow?.after,
                    zone
                )
                viewModel.cancelAndJoin()
            }
        } finally {
            TimeZone.setDefault(default)
        }
    }

    @Test
    fun pickedTimesSetTheWindowsEnds() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        viewModel.setTimeWindow(false)

        viewModel.openPicker(AutoTimerEditPick.TimeFrom)
        viewModel.onTimePicked(21, 15)
        viewModel.openPicker(AutoTimerEditPick.TimeTo)
        viewModel.onTimePicked(0, 30)

        assertEquals(
            ClockWindow(LocalTime.of(21, 15), LocalTime.of(0, 30)),
            viewModel.uiState.value.draft.timeWindow
        )
        assertNull(viewModel.uiState.value.picker)
    }

    @Test
    fun filterTextLeftInTheFieldIsSaved() = runBlocking<Unit> {
        val viewModel = edit()
        viewModel.editing()
        viewModel.setFilterKind(FilterKind.ExcludeTitle)
        viewModel.filterText.set(" Wiederholung ")

        viewModel.save()
        viewModel.saved()

        val query = receiver.requestsTo(EDIT).single().requestUrl!!
        assertEquals(listOf("Vorschau", "Wiederholung"), query.queryParameterValues("!title"))
        assertEquals("", viewModel.filterText.text)
    }

    @Test
    fun aChannelWithACommaIsLeftOutAndSaidSo() = runBlocking<Unit> {
        val viewModel = edit()
        val before = viewModel.editing().draft.targets
        val iptv = Target.Channel("4097:0:1:0:0:0:0:0:0:0:http%3a//tv/a,b:IPTV", "IPTV")
        val extra = Target.Channel("1:0:19:283D:3FB:1:C00000:0:0:0:", "Das Erste HD")

        viewModel.addTargets(listOf(iptv, extra))

        assertEquals(before + extra, viewModel.uiState.value.draft.targets)
        assertEquals(
            UiText.Resource(R.string.autotimer_targets_skipped),
            viewModel.uiState.value.userMessage
        )
    }

    private fun edit(name: String = "dreamDroid test Wilsberg") = viewModel(handle(1, name))

    private fun create() = viewModel(handle(AutoTimerEditViewModel.NEW_ID, ""))

    private fun handle(id: Int, name: String) = SavedStateHandle(
        mapOf(AutoTimerEdit::id.name to id, AutoTimerEdit::name.name to name)
    )

    private fun otherProfile(home: Profile) = Profile().apply {
        id = 8
        name = "other"
        host = home.host
        port = home.port
    }

    private fun utcMillis(localMidnight: Instant): Long =
        localMidnight.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli()

    private fun throughBundle(handle: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(handle.keys().associateWith { key -> roundTrip(handle.get<Any>(key)) })

    private fun roundTrip(value: Any?): Any? {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }

    private fun viewModel(handle: SavedStateHandle) =
        AutoTimerEditViewModel(handle, autoTimers, timers, sessions).also { viewModels += it }

    private suspend fun AutoTimerEditViewModel.editing(): AutoTimerEditUiState =
        withTimeout(TIMEOUT) { uiState.first { it.content == AutoTimerEditContent.Editing } }

    private suspend fun AutoTimerEditViewModel.saved(): AutoTimerPreview =
        withTimeout(TIMEOUT) { uiState.first { it.saved != null } }.saved!!

    private companion object {
        const val TIMEOUT = 5_000L
        const val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val LOCATIONS = "/web/getlocations"
        const val TAGS = "/web/gettags"
        const val EXTERNALS = "/web/external"
        const val LIST = "/autotimer"
        const val EDIT = "/autotimer/edit"
    }
}
