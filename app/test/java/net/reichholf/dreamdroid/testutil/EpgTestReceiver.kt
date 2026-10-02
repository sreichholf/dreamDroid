package net.reichholf.dreamdroid.testutil

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A [MockWebServer] receiver as the active profile, an in-memory database, and the real
 * [EpgRepository] and [net.reichholf.dreamdroid.data.ServiceRepository] over both. The
 * session starts Online.
 */
class EpgTestReceiver {
    val server = MockWebServer()
    val profiles = TestProfiles()
    val sessions: SessionConnectionHolder = profiles.sessions
    val services = profiles.services
    val repository = EpgRepository(
        receiverApis(profiles.repository),
        profiles.repository,
        profiles.database,
        sessions,
        services
    )

    /** Answer for every request. Defaults to the two-event `epgservice.xml`. */
    @Volatile
    var answer: (RecordedRequest) -> MockResponse = {
        MockResponse().setBody(loadWebFixture("epgservice.xml"))
    }

    private val recorded = MutableStateFlow<List<RecordedRequest>>(emptyList())

    /** Requests so far, oldest first. */
    val requests: List<RecordedRequest>
        get() = recorded.value

    /** The requests to [path]. */
    fun requestsTo(path: String): List<RecordedRequest> =
        requests.filter { it.requestUrl?.encodedPath == path }

    /** Waits until [count] requests to [path] arrived, and returns them. */
    suspend fun awaitRequestsTo(path: String, count: Int): List<RecordedRequest> =
        awaitRequests(count) { it.requestUrl?.encodedPath == path }

    /** Waits until [count] requests that [matches] accepts arrived, and returns them. */
    suspend fun awaitRequests(
        count: Int,
        matches: (RecordedRequest) -> Boolean
    ): List<RecordedRequest> = withRealTimeout(5_000L) {
        recorded.map { all -> all.filter(matches) }.first { it.size >= count }
    }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded.update { it + request }
                return answer(request)
            }
        }
        server.start()
        profiles.repository.setCurrent(
            Profile().apply {
                id = PROFILE_ID
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
        sessions.onSuccess()
    }

    fun stop() {
        server.shutdown()
        profiles.repository.clearCurrent()
    }

    /** Writes a MultiEPG chunk of [bouquetRef] covering [atSec] with [events]. */
    suspend fun writeChunk(bouquetRef: String, atSec: Long, events: List<EpgEventEntity>) {
        val chunk = MultiEpgWindows.chunkContaining(atSec)
        profiles.database.epgDao().replaceChunk(
            EpgChunkMetaEntity(PROFILE_ID, bouquetRef, chunk.startSec, chunk.endSec, 1L),
            events
        )
    }

    /** Writes [tabs] as the TV tab strip, as the hub does when it loaded the bouquets. */
    suspend fun writeTabStrip(vararg tabs: Service) {
        profiles.database.rosterDao().replaceTabStrip(
            PROFILE_ID,
            "TV",
            tabs.mapIndexed { index, tab ->
                BouquetTabEntity(PROFILE_ID, "TV", index, tab.reference, tab.name)
            }
        )
    }

    fun goOffline() {
        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
    }

    companion object {
        const val PROFILE_ID = 7
        const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val CHANNEL = "1:0:1:6DCA:44C:1:C00000:0:0:0:"

        fun event(
            title: String,
            start: Long,
            duration: Long = 3600,
            service: String = CHANNEL,
            bouquetRef: String = BOUQUET
        ): EpgEventEntity = EpgEventEntity(
            profileId = PROFILE_ID,
            bouquetRef = bouquetRef,
            serviceRef = service,
            eventId = title,
            start = start,
            duration = duration,
            title = title,
            description = "",
            descriptionExtended = "",
            serviceName = "Das Erste HD"
        )
    }
}
