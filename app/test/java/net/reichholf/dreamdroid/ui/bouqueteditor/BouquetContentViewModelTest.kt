package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetEditorRepository
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [BouquetContentViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class BouquetContentViewModelTest {
    private val receiver = TestReceiver()
    private val sessions = receiver.profiles.sessions
    private val services = receiver.profiles.services
    private val editor = BouquetEditorRepository(
        enigmaClients(receiver.repository),
        receiver.repository,
        services
    )
    private val viewModels = mutableListOf<BouquetContentViewModel>()
    private val others = mutableListOf<TestReceiver>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        sessions.onSuccess()
        receiver.respond(GET_SERVICES, fixture("getservices_favourites_tv.xml"))
        receiver.respond(BACKUP, fixture("result_backup.xml"))
        EDITS.forEach { receiver.respond(it, simpleResult(true, "Done.")) }
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        others.forEach { it.shutdown() }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheBouquetAndTellsTheKindsApart() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()

        assertEquals(UiText.Raw("Favourites (TV)"), viewModel.uiState.value.title)
        assertEquals(BOUQUET, receiver.requestsTo(GET_SERVICES).single().query("sRef"))
        assertEquals(60, rows.size)
        assertEquals(BouquetEntryKind.Service, rows[0].entry.kind)
        assertEquals(
            listOf("Doku", "Sport", "Old"),
            rows.filter { it.entry.kind == BouquetEntryKind.Marker }.map { it.entry.name }
        )
        assertEquals(
            listOf("RBTV", "DotA2ti"),
            rows.filter { it.entry.kind == BouquetEntryKind.Stream }.map { it.entry.name }
        )
    }

    @Test
    fun alternativeIsShownButNotRenamed() = runBlocking {
        receiver.respond(GET_SERVICES, fixture("getservices_with_alternative.xml"))
        val viewModel = viewModel()
        val rows = viewModel.ready()

        assertEquals(
            listOf(
                BouquetEntryKind.Service,
                BouquetEntryKind.Marker,
                BouquetEntryKind.Service,
                BouquetEntryKind.Alternative
            ),
            rows.map { it.entry.kind }
        )
        viewModel.onItemMenu(rows[3])
        assertEquals(
            listOf(
                BouquetEntryAction.InsertMarker,
                BouquetEntryAction.MoveUp,
                BouquetEntryAction.Remove
            ),
            viewModel.uiState.value.menu?.actions
        )
        viewModel.openRename(rows[3])
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun moveSendsOneMoveWithTheFinalPosition() = runBlocking {
        val viewModel = viewModel()
        val doku = viewModel.ready()[21]

        viewModel.move(doku.key, 0)

        assertEquals(doku, viewModel.rows().first())
        viewModel.settled()
        val move = receiver.requestsTo(MOVE_SERVICE).single()
        assertEquals(BOUQUET, move.query("sBouquetRef"))
        assertEquals(doku.entry.reference, move.query("sRef"))
        assertEquals("0", move.query("position"))
        assertEquals("0", move.query("mode"))
        assertEquals("Doku", viewModel.rows().first().entry.name)
        // Its own edit does not load the list again.
        assertEquals(1, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun failedMoveLoadsAgainAndSaysWhy() = runBlocking {
        receiver.respond(MOVE_SERVICE, simpleResult(false, "Cannot move."))
        val viewModel = viewModel()
        val rows = viewModel.ready()

        viewModel.move(rows[1].key, 0)
        val state = viewModel.settled()

        assertEquals(UiText.Raw("Cannot move."), state.userMessage)
        assertEquals(rows.map { it.entry }, viewModel.rows().map { it.entry })
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun renameKeepsThePlaceWithTheNextEntryAndLoadsAgain() = runBlocking {
        receiver.respond(GET_SERVICES, fixture("getservices_with_alternative.xml"))
        val viewModel = viewModel()
        val rows = viewModel.ready()
        receiver.respond(GET_SERVICES, fixture("getservices_after_rename.xml"))

        viewModel.onMenuAction(rows[0], BouquetEntryAction.Rename)
        assertEquals("BR Fernsehen Süd HD", viewModel.name.text)
        viewModel.name.set(" BR Süd ")
        viewModel.confirmRename()
        viewModel.settled()

        val rename = receiver.requestsTo(RENAME).single()
        assertEquals(BOUQUET, rename.query("sBouquetRef"))
        assertEquals(rows[0].entry.reference, rename.query("sRef"))
        assertEquals(rows[1].entry.reference, rename.query("sRefBefore"))
        assertEquals("BR Süd", rename.query("newName"))
        assertEquals("Das Erste HD", viewModel.rows().last().entry.name)
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun renameOfTheLastEntryAppends() = runBlocking {
        val viewModel = viewModel()
        val last = viewModel.ready().last()

        viewModel.openRename(last)
        viewModel.name.set("Renamed")
        viewModel.confirmRename()
        viewModel.settled()

        val rename = receiver.requestsTo(RENAME).single()
        assertEquals(last.entry.reference, rename.query("sRef"))
        assertEquals("", rename.query("sRefBefore"))
    }

    @Test
    fun blankNameIsRejectedWithoutARequest() = runBlocking {
        val viewModel = viewModel()
        viewModel.openRename(viewModel.ready()[0])

        viewModel.name.set("  ")
        viewModel.confirmRename()

        val state = viewModel.uiState.value
        assertEquals(UiText.Resource(R.string.bouquet_name_empty), state.nameError)
        assertTrue(state.dialog is BouquetContentDialog.Rename)
        assertTrue(receiver.requestsTo(RENAME).isEmpty())
    }

    @Test
    fun markerGoesAtTheEndOrAboveTheRow() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()

        viewModel.openAddMarker()
        viewModel.name.set("News")
        viewModel.confirmAddMarker()
        viewModel.settled()
        viewModel.onMenuAction(rows[4], BouquetEntryAction.InsertMarker)
        viewModel.name.set("Private")
        viewModel.confirmAddMarker()
        viewModel.settled()

        val (appended, inserted) = receiver.requestsTo(ADD_MARKER)
        assertEquals(BOUQUET, appended.query("sBouquetRef"))
        assertEquals("News", appended.query("Name"))
        assertEquals("", appended.query("sRefBefore"))
        assertEquals("Private", inserted.query("Name"))
        assertEquals(rows[4].entry.reference, inserted.query("sRefBefore"))
        assertEquals(3, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun removeHidesTheEntryAtOnce() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()
        viewModel.onMenuAction(rows[0], BouquetEntryAction.Remove)

        viewModel.confirmRemove()

        assertEquals(rows.drop(1), viewModel.rows())
        viewModel.settled()
        val remove = receiver.requestsTo(REMOVE_SERVICE).single()
        assertEquals(BOUQUET, remove.query("sBouquetRef"))
        assertEquals(rows[0].entry.reference, remove.query("sRef"))
    }

    @Test
    fun blockedSessionTakesNoEdit() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()

        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
        withTimeout(TIMEOUT) { viewModel.uiState.first { it.blocked } }
        viewModel.move(rows[1].key, 0)
        viewModel.openAddMarker()
        viewModel.openRename(rows[0])

        assertEquals(rows, viewModel.rows())
        assertNull(viewModel.uiState.value.dialog)
        assertTrue(receiver.requestsTo(MOVE_SERVICE).isEmpty())
    }

    @Test
    fun servicesAddedElsewhereLoadTheListAgain() = runBlocking<Unit> {
        receiver.respond(GET_SERVICES, fixture("getservices_with_alternative.xml"))
        val viewModel = viewModel()
        viewModel.ready()
        receiver.respond(GET_SERVICES, fixture("getservices_after_rename.xml"))

        // What the add-services screen does while this list waits below it.
        assertTrue(editor.addServices(BOUQUET, listOf("1:0:19:283D:3FB:1:C00000:0:0:0:")).succeeded)

        withTimeout(TIMEOUT) {
            viewModel.uiState.first { state ->
                val content = state.content as? BouquetContentList.Ready
                content?.rows?.lastOrNull()?.entry?.kind == BouquetEntryKind.Service
            }
        }
    }

    @Test
    fun editOfAnotherBouquetKeepsTheList() = runBlocking {
        val viewModel = viewModel()
        viewModel.ready()

        services.onBouquetsEdited(listOf(OTHER, TV_ROOTS[0]))

        // The collector runs on the unconfined main dispatcher: a reload would have begun.
        assertFalse(viewModel.uiState.value.refreshing)
        viewModel.cancelAndJoin()
        assertEquals(1, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun noEditWhileTheListLoadsAgain() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()
        val list = receiver.hold(GET_SERVICES)

        viewModel.reload()
        assertTrue(list.arrived.await(TIMEOUT, TimeUnit.MILLISECONDS))
        val refreshing = viewModel.uiState.value
        viewModel.move(rows[1].key, 0)
        viewModel.openAddMarker()
        list.release()
        viewModel.ready()

        assertTrue(refreshing.refreshing)
        assertFalse(refreshing.editable)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(rows, viewModel.rows())
        assertTrue(receiver.requestsTo(MOVE_SERVICE).isEmpty())
    }

    @Test
    fun moveOfARepeatedReferenceLoadsTheBoxOrder() = runBlocking {
        receiver.respond(
            GET_SERVICES,
            serviceList(
                ERSTE to "Das Erste HD",
                SPACER to "Spacer",
                ZDF to "ZDF HD",
                SPACER to "Spacer"
            )
        )
        val viewModel = viewModel()
        val rows = viewModel.ready()
        // The box moves the first spacer, not the one dragged.
        receiver.respond(
            GET_SERVICES,
            serviceList(
                SPACER to "Spacer",
                ERSTE to "Das Erste HD",
                ZDF to "ZDF HD",
                SPACER to "Spacer"
            )
        )

        viewModel.move(rows[3].key, 0)
        viewModel.settled()

        assertEquals(SPACER, receiver.requestsTo(MOVE_SERVICE).single().query("sRef"))
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
        assertEquals(
            listOf(SPACER, ERSTE, ZDF, SPACER),
            viewModel.rows().map { it.entry.reference }
        )
    }

    @Test
    fun removeOfARepeatedMarkerLoadsTheBoxOrder() = runBlocking {
        val marker = "1:64:1:0:0:0:0:0:0:0::"
        receiver.respond(
            GET_SERVICES,
            serviceList("${marker}News" to "News", ERSTE to "Das Erste HD", "${marker}Old" to "Old")
        )
        val viewModel = viewModel()
        val rows = viewModel.ready()
        viewModel.onMenuAction(rows[2], BouquetEntryAction.Remove)

        viewModel.confirmRemove()
        viewModel.settled()

        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun profileChangeClosesTheListAndSendsNothing() = runBlocking {
        val viewModel = viewModel()
        val rows = viewModel.ready()
        viewModel.openAddMarker()

        val other = TestReceiver(receiver.profiles).also { others += it }
        other.start()

        val state = withTimeout(TIMEOUT) { viewModel.uiState.first { it.closed } }
        assertNull(state.dialog)
        assertFalse(state.editable)
        viewModel.move(rows[1].key, 0)
        viewModel.reload()
        viewModel.cancelAndJoin()
        assertTrue(other.requests.isEmpty())
        assertTrue(receiver.requestsTo(MOVE_SERVICE).isEmpty())
    }

    private fun serviceList(vararg entries: Pair<String, String>): String =
        entries.joinToString("", "<e2servicelist>", "</e2servicelist>") { (ref, name) ->
            val marker = if (ref.startsWith("1:64:") || ref.startsWith("1:832:")) 1 else 0
            "<e2service><e2servicereference>$ref</e2servicereference>" +
                "<e2servicename>$name</e2servicename><e2serviceisgroup>0</e2serviceisgroup>" +
                "<e2serviceismarker>$marker</e2serviceismarker>" +
                "<e2serviceisprotected>0</e2serviceisprotected>" +
                "<e2serviceisstream>0</e2serviceisstream></e2service>"
        }

    private fun viewModel() = BouquetContentViewModel(
        SavedStateHandle(
            mapOf("bouquetRef" to BOUQUET, "bouquetName" to "Favourites (TV)", "mode" to "Tv")
        ),
        editor,
        services,
        sessions
    ).also { viewModels += it }

    private suspend fun BouquetContentViewModel.ready() = withTimeout(TIMEOUT) {
        uiState.first { it.content is BouquetContentList.Ready && !it.refreshing }
    }.let { (it.content as BouquetContentList.Ready).rows }

    /** Waits for the edit started last, and the list after it, to finish. */
    private suspend fun BouquetContentViewModel.settled(): BouquetContentUiState =
        withTimeout(TIMEOUT) { uiState.first { !it.pending } }

    private fun BouquetContentViewModel.rows() =
        (uiState.value.content as BouquetContentList.Ready).rows

    private fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

    private fun fixture(name: String): String = loadWebFixture("bouqueteditor/$name")

    private companion object {
        const val TIMEOUT = 5_000L
        const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val OTHER =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
        const val ERSTE = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        const val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val SPACER = "1:832:D:0:0:0:0:0:0:0:"
        const val GET_SERVICES = "/web/getservices"
        const val BACKUP = "/bouqueteditor/web/backup"
        const val MOVE_SERVICE = "/bouqueteditor/web/moveservice"
        const val REMOVE_SERVICE = "/bouqueteditor/web/removeservice"
        const val RENAME = "/bouqueteditor/web/renameservice"
        const val ADD_MARKER = "/bouqueteditor/web/addmarkertobouquet"
        const val ADD_SERVICE = "/bouqueteditor/web/addservicetobouquet"
        val EDITS = listOf(MOVE_SERVICE, REMOVE_SERVICE, RENAME, ADD_MARKER, ADD_SERVICE)
    }
}
