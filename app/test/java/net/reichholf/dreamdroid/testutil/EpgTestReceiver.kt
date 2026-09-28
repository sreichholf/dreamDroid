package net.reichholf.dreamdroid.testutil

import java.util.Collections
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A [MockWebServer] receiver as the active profile, an in-memory database, and the real
 * [EpgRepository] over both. The session starts Online.
 */
class EpgTestReceiver {
    val server = MockWebServer()
    val profiles = TestProfiles()
    val sessions = SessionConnectionHolder()
    val repository = EpgRepository(
        EnigmaClientFactory(profiles.repository),
        profiles.repository,
        profiles.database,
        sessions,
        profiles.context
    )

    /** Answer for every request. Defaults to the two-event `epgservice.xml`. */
    @Volatile
    var answer: (RecordedRequest) -> MockResponse = {
        MockResponse().setBody(loadWebFixture("epgservice.xml"))
    }

    private val recorded = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    /** Requests so far, oldest first. */
    val requests: List<RecordedRequest>
        get() = synchronized(recorded) { recorded.toList() }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return answer(request)
            }
        }
        server.start()
        // EnigmaHttp still reads ProfileRepository.get() for the XML dump flag.
        ProfileRepository.install(profiles.repository)
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
