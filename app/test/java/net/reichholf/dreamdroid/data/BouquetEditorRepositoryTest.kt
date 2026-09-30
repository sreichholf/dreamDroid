package net.reichholf.dreamdroid.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.room.ServiceRosterEntity
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [BouquetEditorRepository] against a [TestReceiver] and an in-memory database. */
class BouquetEditorRepositoryTest {
    private val receiver = TestReceiver()
    private val services = receiver.profiles.services
    private val rosterDao = receiver.profiles.database.rosterDao()
    private val repository =
        BouquetEditorRepository(enigmaClients(receiver.repository), receiver.repository, services)

    @BeforeEach
    fun setUp() {
        receiver.start()
        receiver.respond(BACKUP, fixture("result_backup.xml"))
        EDITS.forEach { receiver.respond(it, simpleResult(true, "Done.")) }
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun availableWhenWebExternalsListTheBouquetEditor() = runBlocking {
        receiver.respond(EXTERNALS, loadWebFixture("bouqueteditor/web_external.xml"))

        assertEquals(true, repository.isAvailable().value)
    }

    @Test
    fun notAvailableWithoutTheBouquetEditor() = runBlocking {
        receiver.respond(
            EXTERNALS,
            "<e2webifexternals><e2webifexternal><e2path>autotimer</e2path>" +
                "</e2webifexternal></e2webifexternals>"
        )

        assertEquals(false, repository.isAvailable().value)
    }

    @Test
    fun failedExternalsAreNoAnswer() = runBlocking {
        receiver.fail(EXTERNALS)

        val response = repository.isAvailable()

        assertNull(response.value)
        assertNotNull(response.error)
    }

    @Test
    fun bouquetsReadTheModeIndex() = runBlocking {
        receiver.respond(GET_SERVICES, fixture("getservices_roots_radio.xml"))

        val bouquets = repository.bouquets(BouquetMode.Radio).value!!

        assertEquals(listOf("Favourites (Radio)", "SKY (Radio)"), bouquets.map { it.name })
        assertTrue(bouquets.all { it.kind == BouquetEntryKind.Bouquet })
        assertEquals(RADIO_ROOTS[0], receiver.requestsTo(GET_SERVICES).single().query("sRef"))
    }

    @Test
    fun entriesClassifyTheBouquetRows() = runBlocking {
        receiver.respond(GET_SERVICES, fixture("getservices_with_alternative.xml"))

        val kinds = repository.entries(FAVOURITES).value!!.map { it.kind }

        assertEquals(
            listOf(
                BouquetEntryKind.Service,
                BouquetEntryKind.Marker,
                BouquetEntryKind.Service,
                BouquetEntryKind.Alternative
            ),
            kinds
        )
        assertEquals(FAVOURITES, receiver.requestsTo(GET_SERVICES).single().query("sRef"))
    }

    @Test
    fun sourcesAskSatellitesProvidersAndAllServices() = runBlocking {
        receiver.respond(SATELLITES, fixture("satelliteslist_tv.xml"))
        receiver.respond(GET_SERVICES, fixture("getservices_empty.xml"))

        val satellites = repository.satellites(BouquetMode.Tv).value!!
        repository.providers(BouquetMode.Tv)
        repository.allServices(BouquetMode.Radio)

        assertTrue(satellites.isNotEmpty())
        assertTrue(satellites.all { it.kind == BouquetEntryKind.Directory })
        assertEquals("0", receiver.requestsTo(SATELLITES).single().query("mode"))
        assertEquals(
            listOf(TV_ROOTS[1], RADIO_ROOTS[2]),
            receiver.requestsTo(GET_SERVICES).map { it.query("sRef") }
        )
    }

    @Test
    fun bouquetEditsSendThePluginParameters() = runBlocking {
        repository.addBouquet(BouquetMode.Tv, "News")
        repository.removeBouquet(BouquetMode.Radio, FAVOURITES)
        repository.moveBouquet(BouquetMode.Tv, FAVOURITES, 2)
        repository.renameBouquet(BouquetMode.Tv, FAVOURITES, "Mine")

        assertEquals(mapOf("name" to "News", "mode" to "0"), params(ADD_BOUQUET))
        assertEquals(
            mapOf("sBouquetRef" to FAVOURITES, "mode" to "1"),
            params(REMOVE_BOUQUET)
        )
        assertEquals(
            mapOf("sBouquetRef" to FAVOURITES, "mode" to "0", "position" to "2"),
            params(MOVE_BOUQUET)
        )
        assertEquals(
            mapOf("sRef" to FAVOURITES, "mode" to "0", "newName" to "Mine"),
            params(RENAME)
        )
    }

    @Test
    fun serviceEditsSendThePluginParameters() = runBlocking {
        repository.removeService(FAVOURITES, ERSTE)
        repository.moveService(BouquetMode.Tv, FAVOURITES, ERSTE, 3)
        repository.addMarker(FAVOURITES, "News", "")

        assertEquals(mapOf("sBouquetRef" to FAVOURITES, "sRef" to ERSTE), params(REMOVE_SERVICE))
        assertEquals(
            mapOf("sBouquetRef" to FAVOURITES, "sRef" to ERSTE, "position" to "3", "mode" to "0"),
            params(MOVE_SERVICE)
        )
        assertEquals(
            mapOf("sBouquetRef" to FAVOURITES, "Name" to "News", "sRefBefore" to ""),
            params(ADD_MARKER)
        )
    }

    @Test
    fun renameServiceKeepsItsPlaceBeforeTheNextEntry() = runBlocking {
        repository.renameService(FAVOURITES, ERSTE, ZDF, "ARD")

        assertEquals(
            mapOf(
                "sBouquetRef" to FAVOURITES,
                "sRef" to ERSTE,
                "sRefBefore" to ZDF,
                "newName" to "ARD"
            ),
            params(RENAME)
        )
    }

    @Test
    fun addServicesAppendsInOrder() = runBlocking {
        val result = repository.addServices(FAVOURITES, listOf(ERSTE, ZDF))

        assertTrue(result.succeeded)
        val requests = receiver.requestsTo(ADD_SERVICE)
        assertEquals(listOf(ERSTE, ZDF), requests.map { it.query("sRef") })
        requests.forEach {
            assertEquals(FAVOURITES, it.query("sBouquetRef"))
            assertEquals("", it.query("sRefBefore"))
            assertNull(it.query("Name"))
        }
    }

    @Test
    fun duplicateServiceIsRejectedAndTheRestIsStillSent() = runBlocking {
        receiver.respond(ADD_SERVICE, fixture("result_addservice_duplicate.xml"))

        val result = repository.addServices(FAVOURITES, listOf(ERSTE, ZDF))

        assertFalse(result.succeeded)
        val failure = result.response.error?.failure as EnigmaFailure.BoxRejected
        assertTrue(failure.stateText.startsWith("Service Das Erste HD already exists"))
        assertEquals(2, receiver.requestsTo(ADD_SERVICE).size)
    }

    @Test
    fun backupRunsOncePerProfileUntilReset() = runBlocking {
        val first = repository.removeService(FAVOURITES, ERSTE)
        val second = repository.removeService(FAVOURITES, ZDF)

        assertEquals("dreamdroid_pretest.tar", first.backup?.value?.stateText)
        assertNull(second.backup)
        val backups = receiver.requestsTo(BACKUP)
        assertEquals(1, backups.size)
        assertTrue(backups.single().query("Filename")!!.matches(Regex("dreamdroid_\\d+")))
        assertEquals(listOf(BACKUP, REMOVE_SERVICE, REMOVE_SERVICE), paths())

        repository.resetBackup()
        assertNotNull(repository.removeService(FAVOURITES, ERSTE).backup)
        assertEquals(2, receiver.requestsTo(BACKUP).size)
    }

    @Test
    fun failedBackupDoesNotBlockTheEdit() = runBlocking {
        receiver.fail(BACKUP)

        val result = repository.removeService(FAVOURITES, ERSTE)

        assertTrue(result.succeeded)
        assertNotNull(result.backup?.error)
        assertEquals(listOf(BACKUP, REMOVE_SERVICE), paths())
    }

    @Test
    fun successfulEditDropsTheRosterAndSignalsTheBouquet() = runBlocking {
        writeRoster(FAVOURITES)
        writeRoster(OTHER)

        assertTrue(repository.removeService(FAVOURITES, ERSTE).succeeded)

        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertTrue(rosterDao.getRoster(PROFILE_ID, FAVOURITES).isEmpty())
        assertEquals(1, rosterDao.rosterContainerCount(PROFILE_ID, OTHER))
        assertEquals(setOf(FAVOURITES), services.bouquetEdits.value.keys)
    }

    @Test
    fun bouquetIndexEditsSignalTheIndex() = runBlocking {
        repository.moveBouquet(BouquetMode.Tv, FAVOURITES, 1)
        val afterMove = services.bouquetEdits.value
        repository.removeBouquet(BouquetMode.Tv, OTHER)

        assertEquals(setOf(TV_ROOTS[0]), afterMove.keys)
        val edits = services.bouquetEdits.value
        assertEquals(setOf(TV_ROOTS[0], OTHER), edits.keys)
        assertTrue(edits.getValue(TV_ROOTS[0]) > afterMove.getValue(TV_ROOTS[0]))
    }

    @Test
    fun partlyRejectedAddDropsTheRosterAndReportsTheRejection() = runBlocking {
        // The first is rejected as a duplicate, the second goes in.
        receiver.respondOnce(ADD_SERVICE, fixture("result_addservice_duplicate.xml"))
        writeRoster(FAVOURITES)

        val result = repository.addServices(FAVOURITES, listOf(ERSTE, ZDF))

        assertFalse(result.succeeded)
        assertTrue(result.response.error?.failure is EnigmaFailure.BoxRejected)
        assertEquals(2, receiver.requestsTo(ADD_SERVICE).size)
        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertEquals(setOf(FAVOURITES), services.bouquetEdits.value.keys)
    }

    @Test
    fun cancelledEditStillDropsTheRoster() = runBlocking {
        writeRoster(FAVOURITES)
        val remove = receiver.hold(REMOVE_SERVICE)
        val job = launch(Dispatchers.IO) { repository.removeService(FAVOURITES, ERSTE) }
        assertTrue(remove.arrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        // Back while the box works on it: the edit may still land.
        job.cancel()
        remove.release()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertEquals(setOf(FAVOURITES), services.bouquetEdits.value.keys)
    }

    @Test
    fun unansweredEditDropsTheRoster() = runBlocking {
        receiver.fail(REMOVE_SERVICE)
        writeRoster(FAVOURITES)

        assertFalse(repository.removeService(FAVOURITES, ERSTE).succeeded)

        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertEquals(setOf(FAVOURITES), services.bouquetEdits.value.keys)
    }

    @Test
    fun removedBouquetDropsItsRoster() = runBlocking {
        writeRoster(FAVOURITES)

        assertTrue(repository.removeBouquet(BouquetMode.Tv, FAVOURITES).succeeded)

        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
    }

    @Test
    fun rejectedEditKeepsTheRosterAndSignalsNothing() = runBlocking {
        receiver.respond(REMOVE_SERVICE, fixture("result_removeservice_missing.xml"))
        writeRoster(FAVOURITES)

        val result = repository.removeService(FAVOURITES, ERSTE)

        assertFalse(result.succeeded)
        assertTrue(result.response.error?.failure is EnigmaFailure.BoxRejected)
        assertEquals(1, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertTrue(services.bouquetEdits.value.isEmpty())
    }

    private suspend fun writeRoster(ref: String) {
        rosterDao.replaceRoster(
            PROFILE_ID,
            ref,
            listOf(ServiceRosterEntity(PROFILE_ID, ref, 0, ERSTE, "Das Erste HD", "CHANNEL"))
        )
    }

    private fun params(path: String): Map<String, String?> {
        val url = receiver.requestsTo(path).last().requestUrl!!
        return url.queryParameterNames.filter { it != "sessionid" }
            .associateWith { url.queryParameter(it) }
    }

    private fun paths(): List<String?> = receiver.requests.map { it.requestUrl?.encodedPath }

    private fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

    private fun fixture(name: String): String = loadWebFixture("bouqueteditor/$name")

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val GET_SERVICES = "/web/getservices"
        const val EXTERNALS = "/web/external"
        const val SATELLITES = "/bouqueteditor/web/satelliteslist"
        const val BACKUP = "/bouqueteditor/web/backup"
        const val ADD_BOUQUET = "/bouqueteditor/web/addbouquet"
        const val REMOVE_BOUQUET = "/bouqueteditor/web/removebouquet"
        const val MOVE_BOUQUET = "/bouqueteditor/web/movebouquet"
        const val ADD_SERVICE = "/bouqueteditor/web/addservicetobouquet"
        const val REMOVE_SERVICE = "/bouqueteditor/web/removeservice"
        const val MOVE_SERVICE = "/bouqueteditor/web/moveservice"
        const val RENAME = "/bouqueteditor/web/renameservice"
        const val ADD_MARKER = "/bouqueteditor/web/addmarkertobouquet"
        val EDITS = listOf(
            ADD_BOUQUET,
            REMOVE_BOUQUET,
            MOVE_BOUQUET,
            ADD_SERVICE,
            REMOVE_SERVICE,
            MOVE_SERVICE,
            RENAME,
            ADD_MARKER
        )

        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val OTHER =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
        const val ERSTE = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        const val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
    }
}
