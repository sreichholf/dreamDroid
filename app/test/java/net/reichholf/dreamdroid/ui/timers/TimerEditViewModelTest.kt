package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.LOCATIONS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TAGS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CHANGE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_DELETE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.nav.TimerEdit
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TimerEditViewModel] over the real [TimerRepository] and a [TestReceiver]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEditViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<TimerEditViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        receiver.respond(LOCATIONS, LOCATION_XML)
        receiver.respond(TAGS, TAG_XML)
        sessions.onSuccess()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun routeTimerFillsTheFormAndTheChoices() = runTest {
        val viewModel = viewModel(route(create = false))

        val state = viewModel.ready()

        assertEquals("Sample", viewModel.name.text)
        assertEquals("Desc", viewModel.description.text)
        assertFalse(state.isCreate)
        assertEquals(UiText.Resource(R.string.timer), state.title)
        assertEquals(listOf("/hdd/movie/", "/media/hdd/"), state.locations)
        assertEquals(listOf("News", "Sport"), state.tags)
        assertEquals("Das Erste HD", state.form?.serviceName)
    }

    @Test
    fun theRouteKeepsTheOpenWebifSettingsOfTheTimer() = runTest {
        val listed = Timer(
            reference = REFERENCE,
            allowDuplicate = "0",
            autoAdjust = "1",
            vps = TimerVps(VpsMode.Safe, 1893456000)
        )
        assertEquals(listed, TimerEdit.from(listed, create = false).toTimer())

        val handle = route(create = false).apply {
            set("allowDuplicate", "0")
            set("vpsMode", "Overwrite")
        }
        val timer = viewModel(handle).ready().timer!!

        assertEquals(
            listOf("0", null, TimerVps(VpsMode.Overwrite)),
            listOf(timer.allowDuplicate, timer.autoAdjust, timer.vps)
        )
    }

    @Test
    fun editsSurviveProcessDeath() = runTest {
        val handle = route(create = true)
        val viewModel = viewModel(handle)
        viewModel.ready()

        type(viewModel.name, "Typed")
        viewModel.onEnabledChange(false)
        viewModel.onZapChange(true)
        viewModel.onLocationChange(1)
        viewModel.onTagsChange(listOf(1, 0))
        viewModel.onRepeatedChange(listOf(0, 2))
        viewModel.onServicePicked(Service("1:0:1:6DCB:44D:1:C00000:0:0:0:", "ZDF HD"))
        viewModel.cancelAndJoin()

        val restored = viewModel(handle)
        val form = restored.ready().form!!

        assertEquals("Typed", restored.name.text)
        assertTrue(restored.uiState.value.isCreate)
        assertFalse(form.enabled)
        assertTrue(form.zap)
        assertEquals(1, form.locationIndex)
        assertEquals(listOf("News", "Sport"), form.tags)
        assertEquals(5, form.repeated)
        assertEquals("ZDF HD", form.serviceName)
    }

    @Test
    fun savingSendsTheTypedFieldsAndFinishes() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer changed"))
        val viewModel = viewModel(route(create = false))
        viewModel.ready()
        type(viewModel.name, "Renamed")
        type(viewModel.description, "New description")
        viewModel.onAfterEventChange(1)

        viewModel.save()
        val state = viewModel.uiState.first { it.finished }

        assertNull(state.progress)
        assertNull(state.saveError)
        val url = receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!
        assertEquals("Renamed", url.queryParameter("name"))
        assertEquals("New description", url.queryParameter("description"))
        assertEquals("1", url.queryParameter("afterevent"))
        assertEquals("/hdd/movie/", url.queryParameter("dirname"))
        assertEquals("1", url.queryParameter("deleteOldOnSave"))
        assertEquals(REFERENCE, url.queryParameter("channelOld"))
        viewModel.onFinishHandled()
        assertFalse(viewModel.uiState.value.finished)
    }

    @Test
    fun rejectedSaveShowsTheReceiverText() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(false, "Conflicting timer exists"))
        val viewModel = viewModel(route(create = true))
        viewModel.ready()

        viewModel.save()
        val state = viewModel.uiState.first { it.saveError != null }

        assertEquals(UiText.Raw("Conflicting timer exists"), state.saveError)
        assertNull(state.progress)
        assertFalse(state.finished)
    }

    @Test
    fun deleteRemovesTheOriginalTimer() = runTest {
        receiver.respond(TIMER_DELETE, simpleResult(true, "Timer deleted"))
        val viewModel = viewModel(route(create = false))
        viewModel.ready()
        viewModel.onServicePicked(Service("1:0:1:OTHER", "Other"))

        viewModel.delete()
        viewModel.uiState.first { it.finished }

        val url = receiver.requestsTo(TIMER_DELETE).single().requestUrl!!
        assertEquals(REFERENCE, url.queryParameter("sRef"))
    }

    @Test
    fun creatingHasNothingToDelete() = runTest {
        val viewModel = viewModel(route(create = true))
        viewModel.ready()

        viewModel.delete()

        assertNull(viewModel.uiState.value.progress)
        assertTrue(receiver.requestsTo(TIMER_DELETE).isEmpty())
    }

    @Test
    fun blockedSessionDoesNotSave() = runTest {
        val viewModel = viewModel(route(create = true), SessionConnectionHolder())
        viewModel.ready()
        assertTrue(viewModel.uiState.value.mutationsBlocked)

        viewModel.save()

        assertNull(viewModel.uiState.value.progress)
        assertTrue(receiver.requestsTo(TIMER_CHANGE).isEmpty())
    }

    @Test
    fun blockedFollowsSession() {
        val viewModel = viewModel(route(create = true))

        sessions.onFailure(EnigmaFailure.Auth, hasCache = true)

        assertTrue(viewModel.uiState.value.mutationsBlocked)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        sessions: SessionConnectionHolder = this.sessions
    ) = TimerEditViewModel(handle, receiver.timerRepository(), sessions).also { viewModels += it }

    /** Waits until the locations and tags have arrived. */
    private suspend fun TimerEditViewModel.ready(): TimerEditUiState =
        uiState.first { it.progress == null && it.locations.isNotEmpty() }

    /** Types into [field] the way the text field does, then lets observers see it. */
    private fun type(field: SavedTextField, text: String) {
        field.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    /** The saved state of a [net.reichholf.dreamdroid.ui.nav.TimerEdit] back-stack entry. */
    private fun route(create: Boolean) = SavedStateHandle(
        mapOf(
            "create" to create,
            "reference" to REFERENCE,
            "serviceName" to "Das Erste HD",
            "name" to "Sample",
            "description" to "Desc",
            "disabled" to "0",
            "begin" to "1893456000",
            "end" to "1893459600",
            "justPlay" to "0",
            "afterEvent" to "3",
            "location" to "/hdd/movie/",
            "repeated" to "0",
            "tags" to ""
        )
    )

    private companion object {
        const val REFERENCE = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        const val LOCATION_XML =
            "<e2locations><e2location>/hdd/movie/</e2location>" +
                "<e2location>/media/hdd/</e2location></e2locations>"
        const val TAG_XML = "<e2tags><e2tag>News</e2tag><e2tag>Sport</e2tag></e2tags>"
    }
}
