package net.reichholf.dreamdroid.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import net.reichholf.dreamdroid.enigma.BouquetMode
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.ReceiverFlavor
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Repositories on a profile the check detected as OpenWebif: their requests go to OpenWebif's
 * `/api` and plugin pages, never to `/web`. Answers are the synthetic fixtures under
 * `test/resources/owif`.
 */
class OpenWebifRepositoryTest {
    private val receiver = TestReceiver()
    private val profiles = receiver.repository
    private val clients by lazy { receiverApis(profiles) }

    @BeforeEach
    fun setUp() {
        receiver.start()
        profiles.setDeviceInfo(
            profiles.requireCurrent(),
            DeviceInfo(interfaceVersion = "OWIF 1.5.2"),
            ReceiverFlavor.OpenWebif
        )
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun aBouquetsServicesAndNowNextComeFromTheApi() = runBlocking<Unit> {
        receiver.respond("/api/getservices", loadOwifFixture("getservices.json"))
        receiver.respond("/api/epgnownext", loadOwifFixture("epgnownext.json"))

        val rows = receiver.profiles.services.receiverNowNext(FAVOURITES)

        assertNull(rows.error)
        assertEquals(5, rows.value!!.size)
        assertTrue(rows.value!!.any { it.now != null })
        val list = receiver.requestsTo("/api/getservices").single()
        assertEquals(FAVOURITES, list.query("sRef"))
        assertNull(list.query("hidden"))
        assertEquals(FAVOURITES, receiver.requestsTo("/api/epgnownext").single().query("bRef"))
        assertNoWebRequests()
    }

    @Test
    fun theBouquetEditorListsHiddenServicesAndEditsThroughItsApi() = runBlocking<Unit> {
        receiver.respond("/api/getservices", loadOwifFixture("getservices_hidden.json"))
        receiver.respond(BACKUP, loadOwifFixture("bouqueteditor_backup.json"))
        receiver.respond(ADD_BOUQUET, loadOwifFixture("bouqueteditor_addbouquet.json"))
        val editor = BouquetEditorRepository(clients, profiles, receiver.profiles.services)

        assertEquals(true, editor.isAvailable().value)
        val entries = editor.entries(FAVOURITES)
        val added = editor.addBouquet(BouquetMode.Tv, "News & Sport")

        assertEquals(3, entries.value!!.size)
        val list = receiver.requestsTo("/api/getservices").single()
        assertEquals("1", list.query("hidden"))
        assertTrue(added.succeeded)
        assertEquals("dreamdroid_1700000000.tar", added.backup?.value?.stateText)
        assertEquals("News & Sport", receiver.requestsTo(ADD_BOUQUET).single().query("name"))
        assertNoWebRequests()
    }

    @Test
    fun autoTimerPresenceComesFromAutoTimerGet() = runBlocking<Unit> {
        receiver.respond("/autotimer/get", loadOwifFixture("autotimer/get_17.xml"))
        receiver.respond("/autotimer", loadOwifFixture("autotimer/list_17.xml"))
        val autoTimers = AutoTimerRepository(clients, profiles, TestScope())

        assertEquals(PluginPresence.Present, autoTimers.refreshPresence())
        val load = autoTimers.list() as AutoTimerLoad.Ready

        assertEquals(2, load.entries.size)
        assertNoWebRequests()
    }

    private fun assertNoWebRequests() {
        assertEquals(
            emptyList<String>(),
            receiver.requests.mapNotNull { it.requestUrl?.encodedPath }
                .filter { it.startsWith("/web/") }
        )
    }

    private fun RecordedRequest.query(name: String): String? = requestUrl?.queryParameter(name)

    private companion object {
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val BACKUP = "/bouqueteditor/api/backup"
        const val ADD_BOUQUET = "/bouqueteditor/api/addbouquet"
    }
}
