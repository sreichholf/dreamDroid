package net.reichholf.dreamdroid.enigma

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The `/web` requests [DreamboxWebIfApi] sends for the services, EPG, timer, movie, location and
 * tag calls, and the stream and file URLs it builds.
 */
class DreamboxWebIfApiTest {
    private val server = MockWebServer()

    private val api by lazy {
        DreamboxWebIfApi(
            EnigmaHttp(
                Profile().apply {
                    host = server.hostName
                    port = server.port
                },
                EnigmaOkHttp()
            )
        )
    }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        DreamDroid.enableNowNext()
        server.shutdown()
    }

    @Test
    fun epgNowNextAsksEpgNowNextByBouquet() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api.epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnownext", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
        assertEquals(1, rows.size)
    }

    @Test
    fun withoutNowNextEpgNowNextAsksEpgNowWithOneRowPerEvent() = runBlocking {
        DreamDroid.disableNowNext()
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api.epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.now != null && it.next == null })
    }

    @Test
    fun epgNowNextAsksAProviderByBRef() = runBlocking {
        DreamDroid.disableNowNext()
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        api.epgNowNext(PROVIDERS)

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(PROVIDERS, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
    }

    @Test
    fun epgMultiSendsTheWindowLengthInWholeMinutesRoundedDown() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.epgMulti(BOUQUET, START, START + 90 * 60 + 59)
        api.epgMulti(BOUQUET, START, START + 30)

        val url = takeUrl()
        assertEquals("/web/epgmulti", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("90", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWindowSendsItsLengthInWholeMinutesRoundedUp() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE, START, START + 61)
        api.serviceEpg(SERVICE, START, START)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(SERVICE, url.queryParameter("sRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("2", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWithoutAWindowSendsOnlyTheService() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(setOf("sRef"), url.queryParameterNames)
    }

    @Test
    fun addTimerSendsTimerChangeWithoutAnOldTimer() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.addTimer(TIMER)

        assertEquals(
            "/web/timerchange?$TIMER_QUERY&deleteOldOnSave=0",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun editTimerSendsTheNewTimerAndTheOldOnesKeys() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val old = TIMER.copy(reference = "1:0:1:old", begin = "1699990000", end = "1699993600")

        api.editTimer(old, TIMER)

        assertEquals(
            "/web/timerchange?$TIMER_QUERY&channelOld=1%3A0%3A1%3Aold&beginOld=1699990000" +
                "&endOld=1699993600&deleteOldOnSave=1",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun addTimerForEventSendsServiceAndEventId() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.addTimerForEvent(Event(serviceReference = SERVICE, eventId = "39150"))

        assertEquals(
            "/web/timeraddbyeventid?sRef=$SERVICE_ENCODED&eventid=39150",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun deleteTimerSendsServiceBeginAndEnd() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.deleteTimer(TIMER)

        assertEquals(
            "/web/timerdelete?sRef=$SERVICE_ENCODED&begin=1700000000&end=1700003600",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun timerListAndCleanupSendFixedQueries() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.timers()
        api.cleanupTimers()

        assertEquals("/web/timerlist?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals(
            "/web/timercleanup?cleanup=true",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun moviesSendsTheLocationAndTheTagsJoinedBySpaces() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.movies("/media/hdd/movie/", listOf("news", "kids"))
        api.movies("", emptyList())

        assertEquals(
            "/web/movielist?dirname=%2Fmedia%2Fhdd%2Fmovie%2F&tag=news%20kids",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
        assertEquals("/web/movielist?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun deleteMovieSendsItsReference() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.deleteMovie(Movie(reference = RECORDING_REF))

        assertEquals(
            "/web/moviedelete?sRef=1%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A$RECORDING_ENCODED",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun playMediaSendsTheReferenceAsFile() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.playMedia("4097:0:0:0:0:0:0:0:0:0:http%3a//x/y.mp4:Title")

        assertEquals(
            "/web/mediaplayerplay?file=4097%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A0%3A" +
                "http%253a%2F%2Fx%2Fy.mp4%3ATitle",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun locationsAndTagsReadTheirLists() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "<e2locations><e2location>/media/hdd/movie/</e2location></e2locations>"
            )
        )
        server.enqueue(
            MockResponse().setBody("<e2tags><e2tag>News</e2tag><e2tag>Kids</e2tag></e2tags>")
        )

        assertEquals(listOf("/media/hdd/movie/"), api.locations().value)
        assertEquals(listOf("News", "Kids"), api.tags().value)

        assertEquals("/web/getlocations?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("/web/gettags?", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun downloadRecordingCopiesFileIntoTheDestination() = runBlocking {
        server.enqueue(MockResponse().setBody("recording"))
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = Files.createTempFile("recording", ".ts").toFile()

        try {
            assertNull(api.downloadRecording(RECORDING_PATH, destination))
            assertEquals("recording", destination.readText())
            assertNotNull(api.downloadRecording(RECORDING_PATH, destination))
        } finally {
            destination.delete()
        }

        assertEquals(
            "/file?file=$RECORDING_ENCODED",
            server.takeRequest(5, TimeUnit.SECONDS)!!.path
        )
    }

    @Test
    fun liveStreamOnTheStreamPortLeavesCredentialsOutOfPlainHttp() {
        assertEquals(
            "http://box.local:8001/$SERVICE_ENCODED",
            urlClient().liveStreamUrl(SERVICE)
        )
    }

    @Test
    fun recordingStreamUsesTheFilePortAndRecordingFileTheWebPort() {
        val client = urlClient()
        val movie = Movie(reference = RECORDING_REF, fileName = RECORDING_PATH)

        assertEquals(
            "http://box.local:8080/file?file=$RECORDING_ENCODED",
            client.recordingStreamUrl(movie)
        )
        assertEquals(
            "http://box.local:80/file?file=$RECORDING_ENCODED",
            client.recordingFileUrl(RECORDING_PATH)
        )
    }

    @Test
    fun recordingStreamOverHttpsCarriesTheFileLogin() {
        val client = urlClient { fileSsl = true }
        val movie = Movie(reference = RECORDING_REF, fileName = RECORDING_PATH)

        assertEquals(
            "https://root:secret@box.local:8080/file?file=$RECORDING_ENCODED",
            client.recordingStreamUrl(movie)
        )
    }

    private fun urlClient(configure: Profile.() -> Unit = {}) = DreamboxWebIfApi(
        EnigmaHttp(
            Profile().apply {
                host = "box.local"
                port = 80
                streamPort = 8001
                filePort = 8080
                login = true
                user = "root"
                pass = "secret"
                streamLogin = true
                fileLogin = true
                configure()
            },
            EnigmaOkHttp()
        )
    )

    private fun takeUrl(): HttpUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!

    private companion object {
        const val BOUQUET = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
        const val PROVIDERS = "1:0:1:0:0:0:0:0:0:0:FROM PROVIDERS"
        const val SERVICE = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val START = 1_700_000_000L
        const val SERVICE_ENCODED = "1%3A0%3A19%3A2B66%3A3F3%3A1%3AC00000%3A0%3A0%3A0%3A"
        const val RECORDING_PATH = "/media/hdd/movie/a b.ts"
        const val RECORDING_REF = "1:0:0:0:0:0:0:0:0:0:$RECORDING_PATH"
        const val RECORDING_ENCODED = "%2Fmedia%2Fhdd%2Fmovie%2Fa%20b.ts"

        val TIMER = Timer(
            reference = SERVICE,
            begin = "1700000000",
            end = "1700003600",
            name = "Tagesschau & Wetter",
            description = "Nachrichten",
            location = "/hdd/movie/",
            tags = "news kids",
            eit = "4711",
            disabled = "0",
            justPlay = "0",
            afterEvent = "3",
            repeated = "0"
        )

        /** The timer fields `/web/timerchange` gets for [TIMER], in the order they are sent. */
        const val TIMER_QUERY = "sRef=$SERVICE_ENCODED&begin=1700000000&end=1700003600" +
            "&name=Tagesschau%20%26%20Wetter&description=Nachrichten&dirname=%2Fhdd%2Fmovie%2F" +
            "&tags=news%20kids&eit=4711&disabled=0&justplay=0&afterevent=3&repeated=0"
    }
}
