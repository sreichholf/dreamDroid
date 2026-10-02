package net.reichholf.dreamdroid.enigma

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The requests [OpenWebifApi] sends to OpenWebif's `/api` and how it maps the answers, against
 * the synthetic fixtures in `test/resources/owif` (see the README there for their sources).
 */
class OpenWebifApiTest {
    private val server = MockWebServer()

    private val api by lazy {
        OpenWebifApi(
            EnigmaHttp(
                Profile().apply {
                    host = server.hostName
                    port = server.port
                    streamPort = 8001
                    filePort = 80
                },
                EnigmaOkHttp(),
                WebIfCapabilitiesRepository()
            )
        )
    }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun servicesAsksGetServicesWithoutHiddenAndKeepsNamesAsSent() = runBlocking {
        answer("getservices.json")

        val services = api.services(FAVOURITES).value!!

        assertRequest("/api/getservices", "sRef" to FAVOURITES)
        assertEquals(
            listOf(
                Service("1:64:1:0:0:0:0:0:0:0::Hauptsender", "Hauptsender"),
                Service(DAS_ERSTE, "Das Erste HD"),
                Service(ZDF, "ZDF HD"),
                Service(SKY, "Sky & Co &amp; More"),
                Service(BR, "BR Süd HD")
            ),
            services
        )
    }

    @Test
    fun epgNowNextGroupsByServiceAndPlacesALoneEventByTime() = runBlocking {
        answer("epgnownext.json")

        val rows = api.epgNowNext(FAVOURITES).value!!

        assertRequest("/api/epgnownext", "bRef" to FAVOURITES)
        assertEquals(listOf(DAS_ERSTE, ZDF, SKY), rows.map { it.serviceReference })
        val (erste, zdf, sky) = rows
        assertEquals("Das Erste HD", erste.serviceName)
        assertEquals("Tagesschau" to "Tatort", erste.now?.title to erste.next?.title)
        assertEquals("heute journal", zdf.now?.title)
        assertNull(zdf.next)
        assertNull(sky.now)
        assertEquals("Sky & Co", sky.serviceName)
        assertEquals("Tom & Jerry", sky.next?.title)
        assertEquals("\"Classic\" cartoons", sky.next?.description)
        assertEquals("Cat <3 mouse", sky.next?.descriptionExtended)
        assertEquals("Sky & Co", sky.next?.serviceName)
    }

    @Test
    fun epgAtAsksForTheSingleEventAndPicksTheOneRunning() = runBlocking {
        answer("epgbouquet.json")

        val events = api.epgAt(FAVOURITES, T).value!!

        assertRequest(
            "/api/epgbouquet",
            "bRef" to FAVOURITES,
            "time" to T.toString(),
            "endTime" to "0"
        )
        assertEquals(
            listOf(DAS_ERSTE to "Tagesschau", ZDF to "heute journal"),
            events.map { it.serviceReference to it.title }
        )
    }

    @Test
    fun serviceEpgAsksEpgServiceByServiceAndMapsEachEvent() = runBlocking {
        answer("epgservice.json")

        val events = api.serviceEpg(DAS_ERSTE).value!!

        assertRequest("/api/epgservice", "sRef" to DAS_ERSTE)
        assertEquals(2, events.size)
        val first = events.first()
        assertEquals("4711", first.eventId)
        assertEquals(T.toString(), first.start)
        assertEquals("900", first.duration)
        assertEquals("", first.currentTime)
        assertEquals("Tagesschau", first.title)
        assertEquals("Nachrichten", first.description)
        assertEquals("Line one\nLine <two>", first.descriptionExtended)
        assertEquals(DAS_ERSTE, first.serviceReference)
        assertEquals("Das Erste HD", first.serviceName)
        assertEquals(first.copy(startReadable = "").withReadableTimes(), first)
    }

    @Test
    fun serviceEpgDropsTheFillerRowForNoEvent() = runBlocking {
        answer("epgservice_empty.json")

        assertEquals(emptyList<Event>(), api.serviceEpg(DAS_ERSTE).value)
    }

    @Test
    fun serviceEpgWindowSendsItsLengthInWholeMinutesRoundedUp() = runBlocking {
        answer("epgservice.json")
        answer("epgservice.json")

        api.serviceEpg(DAS_ERSTE, T, T + 61)
        api.serviceEpg(DAS_ERSTE, T, T)

        assertRequest(
            "/api/epgservice",
            "sRef" to DAS_ERSTE,
            "time" to T.toString(),
            "endTime" to "2"
        )
        assertRequest(
            "/api/epgservice",
            "sRef" to DAS_ERSTE,
            "time" to T.toString(),
            "endTime" to "1"
        )
    }

    @Test
    fun epgMultiSendsTheWindowLengthInWholeMinutesRoundedDown() = runBlocking {
        answer("epgmulti.json")
        answer("epgmulti.json")

        val events = api.epgMulti(FAVOURITES, T, T + 90 * 60 + 59).value!!
        api.epgMulti(FAVOURITES, T, T + 30)

        assertRequest(
            "/api/epgmulti",
            "bRef" to FAVOURITES,
            "time" to T.toString(),
            "endTime" to "90"
        )
        assertRequest(
            "/api/epgmulti",
            "bRef" to FAVOURITES,
            "time" to T.toString(),
            "endTime" to "1"
        )
        assertEquals(
            listOf("Tagesschau", "Tatort", "heute journal"),
            events.map { it.title }
        )
    }

    @Test
    fun epgSearchAsksBySearchAndUnescapesTitles() = runBlocking {
        answer("epgsearch.json")

        val events = api.epgSearch("Tatort").value!!

        assertRequest("/api/epgsearch", "search" to "Tatort")
        assertEquals(listOf("Tatort", "Tatort & Polizeiruf"), events.map { it.title })
        assertEquals(listOf("", ""), events.map { it.currentTime })
    }

    @Test
    fun currentServiceUnescapesTheNameAndMapsNowAndNext() = runBlocking {
        answer("getcurrent.json")

        val current = api.currentService().value!!

        assertRequest("/api/getcurrent")
        assertEquals(Service(A_AND_E, "A&E", "A&E Networks"), current.service)
        assertEquals("Crime & Punishment", current.now?.title)
        assertEquals(T.toString(), current.now?.currentTime)
        assertEquals("A&E", current.now?.serviceName)
        assertEquals("Storage Wars", current.next?.title)
    }

    @Test
    fun currentServiceDecodesTheIptvRefOnceAndReadsStartZeroAsNoEvent() = runBlocking {
        answer("getcurrent_iptv.json")

        val current = api.currentService().value!!

        assertEquals(
            Service(
                "4097:0:1:0:0:0:0:0:0:0:http%3a//192.168.0.5%3a8001/stream:Live & Radio",
                "Live & Radio"
            ),
            current.service
        )
        assertEquals("", current.service.provider)
        assertNull(current.now)
        assertNull(current.next)
    }

    @Test
    fun currentServiceKeepsARecordingsOwnTextAsSent() = runBlocking {
        answer("getcurrent_recording.json")

        val now = api.currentService().value!!.now!!

        assertEquals("Tom & Jerry", now.title)
        assertEquals("Q&amp;A <live>", now.description)
        assertEquals("Tom & Jerry", now.serviceName)
    }

    @Test
    fun deviceInfoMapsTheFieldsWebDeviceInfoShows() = runBlocking {
        answer("deviceinfo.json")

        val info = api.deviceInfo().value!!

        assertRequest("/api/deviceinfo")
        assertEquals(
            DeviceInfo(
                guiVersion = "2024-05-10",
                imageVersion = "7.4.0",
                interfaceVersion = "OWIF 1.5.2",
                frontProcessorVersion = "",
                deviceName = "Uno 4K SE",
                frontends = listOf(
                    DeviceFrontend("Tuner A", "Vuplus DVB-S NIM(45308X FBC) (DVB-S2X)")
                ),
                nics = listOf(
                    DeviceNic(
                        "eth0",
                        "00:1d:ec:12:34:56",
                        "True",
                        "192.168.0.20",
                        "192.168.0.1",
                        "255.255.255.0"
                    )
                ),
                hdds = listOf(DeviceHdd("ATA(ST2000LM015-2E81)", "1.8 TB", "1.2 TB"))
            ),
            info
        )
    }

    @Test
    fun signalWithADbStringHasDb() = runBlocking {
        answer("signal.json")

        val signal = api.signal().value!!

        assertRequest("/api/signal")
        assertEquals(Signal("12.50 dB", "63 %", "0", "73 %"), signal)
        assertEquals(12.5, signal.snrDb)
        assertEquals(63, signal.snrPercent)
    }

    @Test
    fun signalWithANumericSnrDbHasNoDb() = runBlocking {
        answer("signal_nodb.json")

        val signal = api.signal().value!!

        assertEquals(Signal("", "81 %", "0", "90 %"), signal)
        assertEquals(Signal.MIN_SNR_DB, signal.snrDb)
    }

    @Test
    fun resultFalseWithAMessageIsABoxRejection() = runBlocking {
        answer("missing_parameter.json")

        val response = api.epgNowNext(FAVOURITES)

        assertNull(response.value)
        assertEquals(
            EnigmaFailure.BoxRejected("Missing mandatory parameter 'bRef'"),
            response.error?.failure
        )
    }

    @Test
    fun anHtmlBodyIsAParseFailure() = runBlocking {
        answer("error404.html")

        val response = api.services(FAVOURITES)

        assertNull(response.value)
        assertEquals(EnigmaFailure.Parse, response.error?.failure)
    }

    @Test
    fun anHtml404IsAnHttpFailure() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(loadOwifFixture("error404.html"))
        )

        val response = api.currentService()

        assertNull(response.value)
        assertEquals(404, (response.error?.failure as EnigmaFailure.Http).code)
    }

    @Test
    fun aJsonArrayIsAParseFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(EnigmaFailure.Parse, api.signal().error?.failure)
    }

    @Test
    fun timersAskTimerListAndKeepTextAsSent() = runBlocking {
        answer("timerlist.json")

        val timers = api.timers().value!!

        assertRequest("/api/timerlist")
        assertEquals(2, timers.size)
        val (tatort, heute) = timers
        assertEquals(
            listOf(
                DAS_ERSTE,
                "Das Erste HD",
                "4712",
                "Tatort",
                "Krimi",
                "Kommissar & Co",
                "0",
                (T + 900).toString(),
                (T + 6300).toString(),
                "5400",
                "0",
                "3",
                "/media/hdd/movie/",
                "Krimi Serie",
                "0",
                "0",
                ""
            ),
            with(tatort) {
                listOf(
                    reference,
                    serviceName,
                    eit,
                    name,
                    description,
                    descriptionExtended,
                    disabled,
                    begin,
                    end,
                    duration,
                    justPlay,
                    afterEvent,
                    location,
                    tags,
                    state,
                    repeated,
                    logEntries
                )
            }
        )
        assertTrue(tatort.beginReadable.isNotEmpty())
        assertEquals(
            listOf("0", "1", "1", "1", T + 900),
            with(tatort) {
                listOf(allowDuplicate, autoAdjust, vpsEnabled, vpsOverwrite, vpsTime?.toLong())
            }
        )
        assertEquals(
            listOf("1", null, "0", "0", null),
            with(heute) { listOf(allowDuplicate, autoAdjust, vpsEnabled, vpsOverwrite, vpsTime) }
        )
        assertEquals(
            listOf("", "1", "1", "0", "", "", "31", "N/A"),
            with(heute) {
                listOf(
                    eit,
                    disabled,
                    justPlay,
                    afterEvent,
                    location,
                    tags,
                    repeated,
                    descriptionExtended
                )
            }
        )
    }

    @Test
    fun addTimerSendsTimerAddWithoutEitAndWithoutEmptyTags() = runBlocking {
        answer("timeradd.json")

        val response = api.addTimer(TATORT_TIMER.copy(tags = "", repeated = ""))

        assertRequest(
            "/api/timeradd",
            "sRef" to DAS_ERSTE,
            "begin" to (T + 900).toString(),
            "end" to (T + 6300).toString(),
            "name" to "Tatort",
            "description" to "Krimi",
            "dirname" to "/media/hdd/movie/",
            "disabled" to "0",
            "justplay" to "0",
            "afterevent" to "3",
            "repeated" to "0"
        )
        assertEquals(SimpleResult("True", "Timer 'Tatort' added"), response.value)
        assertNull(response.error)
    }

    @Test
    fun addTimerNamesAnUnnamedTimerAfterItsService() = runBlocking {
        answer("timeradd.json")

        api.addTimer(TATORT_TIMER.copy(name = " "))

        assertEquals("Das Erste HD", takeQuery()["name"])
    }

    @Test
    fun addTimerWithoutAnyNameIsTheBoxsRejection() = runBlocking {
        answer("timer_name_empty.json")

        val response = api.addTimer(TATORT_TIMER.copy(name = "", serviceName = ""))

        assertEquals("", takeQuery()["name"])
        val message = "The parameter 'name' can't be empty"
        assertEquals(SimpleResult("False", message), response.value)
        assertEquals(EnigmaFailure.BoxRejected(message), response.error?.failure)
    }

    @Test
    fun editTimerSendsTheOldKeysToTimerChange() = runBlocking {
        answer("timerchange.json")

        val old = TATORT_TIMER.copy(begin = T.toString(), end = (T + 5400).toString())
        val response = api.editTimer(old, TATORT_TIMER)

        assertRequest(
            "/api/timerchange",
            "sRef" to DAS_ERSTE,
            "begin" to (T + 900).toString(),
            "end" to (T + 6300).toString(),
            "name" to "Tatort",
            "description" to "Krimi",
            "dirname" to "/media/hdd/movie/",
            "tags" to "Krimi Serie",
            "disabled" to "0",
            "justplay" to "0",
            "afterevent" to "3",
            "repeated" to "0",
            "channelOld" to DAS_ERSTE,
            "beginOld" to T.toString(),
            "endOld" to (T + 5400).toString()
        )
        assertEquals(SimpleResult("True", "Timer 'Tatort' changed"), response.value)
    }

    @Test
    fun editTimerSendsBackTheSettingsTimerChangeWouldReset() = runBlocking {
        answer("timerlist.json")
        val listed = api.timers().value!!.first()
        server.takeRequest(5, TimeUnit.SECONDS)
        answer("timerchange.json")

        api.editTimer(listed, listed.copy(name = "Tatort (neu)"))

        val query = takeQuery()
        assertEquals("Tatort (neu)", query["name"])
        assertEquals(
            mapOf(
                "allow_duplicate" to "0",
                "autoadjust" to "1",
                "vpsplugin_enabled" to "1",
                "vpsplugin_overwrite" to "1",
                "vpsplugin_time" to (T + 900).toString()
            ),
            query.filterKeys {
                it in setOf(
                    "allow_duplicate",
                    "autoadjust",
                    "vpsplugin_enabled",
                    "vpsplugin_overwrite",
                    "vpsplugin_time"
                )
            }
        )
    }

    @Test
    fun aTimerConflictKeepsTheResultAndIsABoxRejection() = runBlocking {
        answer("timer_conflict.json")

        val response = api.addTimer(TATORT_TIMER)

        val message = "Conflicting Timer(s) detected! Tagesschau"
        assertEquals(SimpleResult("False", message), response.value)
        assertEquals(EnigmaFailure.BoxRejected(message), response.error?.failure)
    }

    @Test
    fun addTimerForEventSendsServiceAndEventId() = runBlocking {
        answer("timeraddbyeventid.json")

        val response = api.addTimerForEvent(
            Event(eventId = "4712", serviceReference = DAS_ERSTE, title = "Tatort")
        )

        assertRequest("/api/timeraddbyeventid", "sRef" to DAS_ERSTE, "eventid" to "4712")
        assertEquals(SimpleResult("True", "Timer 'Tatort' added"), response.value)
    }

    @Test
    fun deleteTimerFindsTheTimerByServiceBeginAndEndWithoutEit() = runBlocking {
        answer("timerdelete.json")

        val response = api.deleteTimer(TATORT_TIMER)

        assertRequest(
            "/api/timerdelete",
            "sRef" to DAS_ERSTE,
            "begin" to (T + 900).toString(),
            "end" to (T + 6300).toString()
        )
        assertEquals(
            SimpleResult("True", "The timer 'Tatort' has been deleted successfully"),
            response.value
        )
    }

    @Test
    fun cleanupTimersAsksTimerCleanup() = runBlocking {
        answer("timercleanup.json")

        val response = api.cleanupTimers()

        assertRequest("/api/timercleanup")
        assertEquals(SimpleResult("True", "List of Timers has been cleaned"), response.value)
    }

    @Test
    fun aCommandAnsweredWithHtmlIsAParseFailureWithoutValue() = runBlocking {
        answer("error404.html")

        val response = api.cleanupTimers()

        assertNull(response.value)
        assertEquals(EnigmaFailure.Parse, response.error?.failure)
    }

    @Test
    fun moviesSendTheFirstTagAndKeepThoseWithEveryTag() = runBlocking {
        answer("movielist.json")

        val movies = api.movies(FILME, listOf("Krimi", "Serie")).value!!

        assertRequest(
            "/api/movielist",
            "dirname" to "/media/hdd/movie/Filme %u00E4%u0025/",
            "tag" to "Krimi"
        )
        assertEquals(listOf("Tatort"), movies.map { it.title })
    }

    @Test
    fun moviesInTheDefaultLocationSendNothingAndMapEachRecording() = runBlocking {
        answer("movielist.json")

        val movies = api.movies("", emptyList()).value!!

        assertRequest("/api/movielist")
        assertEquals(2, movies.size)
        val (tatort, alte) = movies
        assertEquals(TATORT_REF, tatort.reference)
        assertEquals("Tatort", tatort.title)
        assertEquals("Krimi", tatort.description)
        assertEquals("Kommissar & Co", tatort.descriptionExtended)
        assertEquals("Das Erste HD", tatort.serviceName)
        assertEquals((T + 120).toString(), tatort.time)
        assertEquals("90:00", tatort.length)
        assertEquals("Krimi Serie", tatort.tags)
        assertEquals(TATORT_REF.substringAfter(":0:0:0:0:0:0:0:0:0:"), tatort.fileName)
        assertEquals("3221225472", tatort.fileSize)
        assertEquals("3072 MB", tatort.fileSizeReadable)
        assertEquals("ZDF HD", alte.serviceName)
        assertEquals("?:??", alte.length)
    }

    @Test
    fun deleteMovieSendsItsReference() = runBlocking {
        answer("moviedelete.json")

        val response = api.deleteMovie(Movie(reference = TATORT_REF))

        assertRequest("/api/moviedelete", "sRef" to TATORT_REF)
        assertEquals(
            SimpleResult("True", "The movie 'Tatort' has been deleted successfully"),
            response.value
        )
    }

    @Test
    fun locationsAndTagsAreTheBoxsLists() = runBlocking {
        answer("getlocations.json")
        answer("gettags.json")

        val locations = api.locations().value
        val tags = api.tags().value

        assertRequest("/api/getlocations")
        assertRequest("/api/gettags")
        assertEquals(listOf("/media/hdd/movie/", "/media/hdd/movie/Filme ä/"), locations)
        assertEquals(listOf("Krimi", "Serie"), tags)
    }

    @Test
    fun playMediaSendsTheReferenceAndAMissingPlayerIsABoxRejection() = runBlocking {
        answer("mediaplayerplay_missing.json")

        val response = api.playMedia(MEDIA_REF)

        assertRequest("/api/mediaplayerplay", "file" to MEDIA_REF)
        assertEquals(
            EnigmaFailure.BoxRejected("Mediaplayer not installed"),
            response.error?.failure
        )
    }

    @Test
    fun streamAndFileUrlsUseTheStreamPortAndFile() {
        val host = server.hostName
        assertEquals(
            "http://$host:8001/1%3A0%3A19%3A283D%3A3FB%3A1%3AC00000%3A0%3A0%3A0%3A",
            api.liveStreamUrl(DAS_ERSTE)
        )
        assertEquals(
            "http://$host:80/file?file=%2Fmedia%2Fhdd%2Fmovie%2Fa%20b.ts",
            api.recordingStreamUrl(Movie(reference = "1:0:0:0:0:0:0:0:0:0:/x", fileName = AB))
        )
        assertEquals(
            "http://$host:${server.port}/file?file=%2Fmedia%2Fhdd%2Fmovie%2Fa%20b.ts",
            api.recordingFileUrl(AB)
        )
    }

    @Test
    fun downloadRecordingWritesTheFile(@TempDir dir: File) = runBlocking {
        val bytes = byteArrayOf(0x47, 0x40, 0x11, 0x10)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Disposition", "attachment;filename=\"a b.ts\"")
                .setHeader("Content-Type", "video/MP2T")
                .setBody(Buffer().write(bytes))
        )
        val out = File(dir, "a.ts")

        val error = api.downloadRecording(AB, out)

        assertRequest("/file", "file" to AB)
        assertNull(error)
        assertArrayEquals(bytes, out.readBytes())
    }

    @Test
    fun downloadRecordingOfAMissingFileIsTheBoxsTextAndNoFile(@TempDir dir: File) = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody(loadOwifFixture("file_not_found.txt"))
        )
        val out = File(dir, "gone.ts")

        val error = api.downloadRecording("/media/hdd/movie/gone.ts", out)

        assertEquals(
            EnigmaFailure.BoxRejected("File '/media/hdd/movie/gone.ts' not found"),
            error?.failure
        )
        assertFalse(out.exists())
    }

    private fun answer(fixture: String) {
        server.enqueue(MockResponse().setBody(loadOwifFixture(fixture)))
    }

    /** The next request's query parameters. */
    private fun takeQuery(): Map<String, String?> {
        val url: HttpUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        return url.queryParameterNames.filter { it.isNotEmpty() }.associateWith {
            url.queryParameter(it)
        }
    }

    /** The next request went to [path] with exactly [params]. */
    private fun assertRequest(path: String, vararg params: Pair<String, String>) {
        val url: HttpUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals(path, url.encodedPath)
        assertEquals(
            params.toMap(),
            // Without parameters the URL ends in a bare "?".
            url.queryParameterNames.filter { it.isNotEmpty() }.associateWith {
                url.queryParameter(it)
            }
        )
    }

    private companion object {
        const val T = 1_700_000_000L
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val DAS_ERSTE = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        const val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val SKY = "1:0:19:EF10:421:1:C00000:0:0:0:"
        const val BR = "1:0:19:2B98:3F5:1:C00000:0:0:0:"
        const val A_AND_E = "1:0:19:7C:6:85:FFFF0000:0:0:0:"
        const val FILME = "/media/hdd/movie/Filme ä%/"
        const val AB = "/media/hdd/movie/a b.ts"
        const val TATORT_REF =
            "1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/Filme ä/20231114 2315 - Das Erste HD - Tatort.ts"
        const val MEDIA_REF = "4097:0:1:0:0:0:0:0:0:0:http%3A//example.com/a.mp4:Clip"
        val TATORT_TIMER = Timer(
            reference = DAS_ERSTE,
            serviceName = "Das Erste HD",
            eit = "4712",
            name = "Tatort",
            description = "Krimi",
            disabled = "0",
            begin = (T + 900).toString(),
            end = (T + 6300).toString(),
            justPlay = "0",
            afterEvent = "3",
            location = "/media/hdd/movie/",
            tags = "Krimi Serie",
            repeated = "0"
        )
    }
}
