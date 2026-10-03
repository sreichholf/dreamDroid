package net.reichholf.dreamdroid.enigma

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerListParser
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadOwifFixture
import net.reichholf.dreamdroid.ui.text.UiText
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
            listOf("0", "1", TimerVps(VpsMode.Overwrite, T + 900)),
            with(tatort) { listOf(allowDuplicate, autoAdjust, vps) }
        )
        assertEquals(
            listOf("1", null, TimerVps(VpsMode.Off)),
            with(heute) { listOf(allowDuplicate, autoAdjust, vps) }
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
    fun setTimerDisabledTogglesOnceWhenTheBoxLandsOnTheRequestedState() = runBlocking {
        answer("timertogglestatus.json")

        val response = api.setTimerDisabled(TATORT_TIMER, disabled = true)

        assertRequest(
            "/api/timertogglestatus",
            "sRef" to DAS_ERSTE,
            "begin" to (T + 900).toString(),
            "end" to (T + 6300).toString()
        )
        val message = "The timer 'Tatort' has been disabled successfully"
        assertEquals(SimpleResult("True", message), response.value)
        assertNull(response.error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun setTimerDisabledTogglesBackWhenTheBoxHadTheOtherState() = runBlocking {
        answer("timertogglestatus_enabled.json")
        answer("timertogglestatus.json")

        val response = api.setTimerDisabled(TATORT_TIMER, disabled = true)

        repeat(2) {
            assertRequest(
                "/api/timertogglestatus",
                "sRef" to DAS_ERSTE,
                "begin" to (T + 900).toString(),
                "end" to (T + 6300).toString()
            )
        }
        assertEquals("The timer 'Tatort' has been disabled successfully", response.value?.stateText)
    }

    @Test
    fun aConflictingEnableIsABoxRejection() = runBlocking {
        answer("timertogglestatus_conflict.json")

        val response = api.setTimerDisabled(TATORT_TIMER, disabled = false)

        assertEquals(
            EnigmaFailure.BoxRejected("Timer 'Tatort' not enabled while Conflict"),
            response.error?.failure
        )
        assertEquals(1, server.requestCount)
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
            Event(eventId = "4712", serviceReference = DAS_ERSTE, title = "Tatort"),
            vps = null
        )

        assertRequest("/api/timeraddbyeventid", "sRef" to DAS_ERSTE, "eventid" to "4712")
        assertEquals(SimpleResult("True", "Timer 'Tatort' added"), response.value)
    }

    @Test
    fun addTimerForEventSendsTheVpsOfTheNewTimer() = runBlocking {
        answer("timeraddbyeventid.json")

        api.addTimerForEvent(
            Event(eventId = "4712", serviceReference = DAS_ERSTE, title = "Tatort"),
            TimerVps(VpsMode.Safe)
        )

        assertRequest(
            "/api/timeraddbyeventid",
            "sRef" to DAS_ERSTE,
            "eventid" to "4712",
            "vpsplugin_enabled" to "1",
            "vpsplugin_overwrite" to "0",
            "vpsplugin_time" to "-1"
        )
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

    @Test
    fun setVolumeSendsTheCommandAndMapsTheNewLevel() = runBlocking {
        answer("vol.json")
        answer("vol.json")
        answer("vol.json")

        val volume = api.setVolume(VolumeCommand.Up).value
        api.setVolume(VolumeCommand.Down)
        api.setVolume(VolumeCommand.Mute)

        assertRequest("/api/vol", "set" to "up")
        assertRequest("/api/vol", "set" to "down")
        assertRequest("/api/vol", "set" to "mute")
        assertEquals(Volume(result = "True", current = "45", muted = "False"), volume)
    }

    @Test
    fun toggleStandbyAsksTheStateAgainBecauseTheAnswerIsFromBefore() = runBlocking {
        answer("powerstate_before.json")
        answer("powerstate_after.json")

        val state = api.setPowerState(PowerCommand.ToggleStandby)

        assertRequest("/api/powerstate", "newstate" to "0")
        assertRequest("/api/powerstate")
        assertEquals(PowerState(isRunning = false), state.value)
        assertNull(state.error)
    }

    @Test
    fun rebootKeepsTheFirstAnswer() = runBlocking {
        answer("powerstate_before.json")

        val state = api.setPowerState(PowerCommand.Reboot)

        assertRequest("/api/powerstate", "newstate" to "2")
        assertEquals(PowerState(isRunning = true), state.value)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aFailedToggleIsNotAskedAgain() = runBlocking {
        answer("error404.html")

        val state = api.setPowerState(PowerCommand.ToggleStandby)

        assertEquals(EnigmaFailure.Parse, state.error?.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun zapSendsTheReference() = runBlocking {
        answer("zap.json")

        val response = api.zap(DAS_ERSTE)

        assertRequest("/api/zap", "sRef" to DAS_ERSTE)
        assertEquals("True", response.value?.state)
    }

    @Test
    fun remoteCommandPicksTheRemoteAndHoldsALongPress() = runBlocking {
        answer("remotecontrol.json")
        answer("remotecontrol.json")

        val response = api.remoteCommand(352, simpleRemote = true, longPress = false)
        api.remoteCommand(352, simpleRemote = false, longPress = true)

        assertRequest("/api/remotecontrol", "command" to "352", "rcu" to "standard")
        assertRequest(
            "/api/remotecontrol",
            "command" to "352",
            "rcu" to "advanced",
            "type" to "long"
        )
        assertEquals(
            SimpleResult("True", "RC command '352' has been issued"),
            response.value
        )
    }

    @Test
    fun sendMessageSendsTextTypeAndTimeout() = runBlocking {
        answer("message.json")

        val response = api.sendMessage("Hallo & Tschüss", "1", "10")

        assertRequest(
            "/api/message",
            "text" to "Hallo & Tschüss",
            "type" to "1",
            "timeout" to "10"
        )
        assertEquals(SimpleResult("True", "Message sent successfully!"), response.value)
    }

    @Test
    fun sleepTimerReadsANumberOfMinutes() = runBlocking {
        answer("sleeptimer.json")

        val timer = api.sleepTimer().value

        assertRequest("/api/sleeptimer")
        assertEquals(SleepTimer("True", "90", "shutdown", "Sleeptimer is enabled"), timer)
    }

    @Test
    fun sleepTimerReadsMinutesAsAString() = runBlocking {
        answer("sleeptimer_powertimer.json")

        assertEquals(
            SleepTimer("False", "30", "standby", "Sleeptimer is disabled"),
            api.sleepTimer().value
        )
    }

    @Test
    fun aSleepTimer404IsATimerThatIsOff() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(loadOwifFixture("error404.html"))
        )

        val response = api.sleepTimer()

        assertEquals(SleepTimer(enabled = "False"), response.value)
        assertNull(response.error)
    }

    @Test
    fun setSleepTimerSendsSetAndReadsTheStoredTimer() = runBlocking {
        answer("sleeptimer_set.json")

        val response = api.setSleepTimer("30", "standby", enabled = true)

        assertRequest(
            "/api/sleeptimer",
            "cmd" to "set",
            "time" to "30",
            "action" to "standby",
            "enabled" to "True"
        )
        assertEquals(
            SleepTimer("True", "30", "standby", "Sleeptimer set to 30 minutes"),
            response.value
        )
        assertNull(response.error)
    }

    @Test
    fun aSleepTimerErrorMessageIsABoxRejection() = runBlocking {
        answer("sleeptimer_standby.json")

        val response = api.setSleepTimer("30", "standby", enabled = true)

        val message = "ERROR: Cannot set SleepTimer while device is in Standby-Mode"
        assertEquals(EnigmaFailure.BoxRejected(message), response.error?.failure)
        assertEquals(SleepTimer("False", "90", "shutdown", message), response.value)
    }

    @Test
    fun setSleepTimerLeavesOutAMissingTime() = runBlocking {
        answer("sleeptimer.json")

        api.setSleepTimer(null, null, enabled = false)

        assertRequest("/api/sleeptimer", "cmd" to "set", "enabled" to "False")
    }

    @Test
    fun screenshotGrabsAJpeg() = runBlocking {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        server.enqueue(
            MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(jpeg))
        )
        server.enqueue(MockResponse())

        val image = api.screenshot().value
        val empty = api.screenshot()

        assertRequest("/grab", "format" to "jpg")
        assertArrayEquals(jpeg, image)
        assertNull(empty.value)
        assertNull(empty.error)
    }

    @Test
    fun a403IsTheIpRejectedFailure(@TempDir dir: File) = runBlocking {
        repeat(3) { forbidden() }

        val read = api.currentService()
        val command = api.zap(DAS_ERSTE)
        val download = api.downloadRecording(AB, File(dir, "a.ts"))

        assertEquals(EnigmaFailure.IpRejected, read.error?.failure)
        assertEquals(EnigmaFailure.IpRejected, command.error?.failure)
        assertEquals(EnigmaFailure.IpRejected, download?.failure)
        assertEquals(
            UiText.Resource(R.string.ip_rejected_error),
            EnigmaFailure.IpRejected.userMessageText()
        )
    }

    @Test
    fun theAutoTimerPluginAndItsApiComeFromAutoTimerGet() = runBlocking {
        answer("autotimer/get_17.xml")
        answer("ajax_at.html")
        answer("autotimer/get_16.xml")
        answer("ajax_at.html")
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(loadOwifFixture("error404.html"))
        )
        answer("ajax_at.html")
        forbidden()

        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_7), api.plugins().value?.autoTimer)
        assertEquals(AutoTimerPlugin.Installed(AutoTimerApi.V1_6), api.plugins().value?.autoTimer)
        assertEquals(AutoTimerPlugin.Missing, api.plugins().value?.autoTimer)
        assertEquals(EnigmaFailure.IpRejected, api.plugins().error?.failure)

        repeat(3) {
            assertRequest("/autotimer/get")
            assertRequest("/ajax/at")
        }
        assertRequest("/autotimer/get")
    }

    @Test
    fun theVpsPluginIsTheVpsCheckboxOfTheAutoTimerForm() = runBlocking {
        answer("autotimer/get_17.xml")
        answer("ajax_at_vps.html")
        answer("autotimer/get_17.xml")
        answer("ajax_at.html")
        answer("autotimer/get_17.xml")
        server.enqueue(MockResponse().setResponseCode(500))
        answer("autotimer/get_17.xml")
        forbidden()

        assertEquals(true, api.plugins().value?.vps)
        assertEquals(false, api.plugins().value?.vps)
        assertEquals(false, api.plugins().value?.vps)
        assertEquals(EnigmaFailure.IpRejected, api.plugins().error?.failure)
    }

    @Test
    fun theBouquetEditorIsAlwaysThereWithoutAsking() = runBlocking {
        assertEquals(true, api.hasBouquetEditor().value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun autoTimerCallsGoToThePluginsXml() = runBlocking {
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/list_17.xml")))
        repeat(3) { server.enqueue(MockResponse().setBody("")) }

        val list = api.autoTimers().value!!
        api.testAutoTimer(AutoTimerId(1))
        api.removeAutoTimer(AutoTimerId(1))
        api.runAutoTimers()

        assertEquals(listOf(AutoTimerId(1), AutoTimerId(2)), list.entries.map { it.id })
        assertRequest("/autotimer")
        assertRequest("/autotimer/test", "id" to "1")
        assertRequest("/autotimer/remove", "id" to "1")
        assertRequest("/autotimer/parse")
    }

    @Test
    fun theEnableSwitchAloneGoesToAutoTimerChange() = runBlocking {
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/change_17.xml")))
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/edit_17.xml")))
        val tatort = autoTimer17(1)

        val switched = api.saveAutoTimer(
            AutoTimerWrite.Change(tatort, tatort.settings.copy(enabled = false))
        )
        api.saveAutoTimer(AutoTimerWrite.Change(tatort, tatort.settings.copy(name = "T")))

        assertEquals("AutoTimer was changed successfully", switched.value?.stateText)
        assertNull(switched.error)
        assertRequest("/autotimer/change", "id" to "1", "enabled" to "0")
        assertRequest(
            "/autotimer/edit",
            "id" to "1",
            "match" to "Tatort",
            "name" to "T",
            "always_zap" to "1"
        )
    }

    @Test
    fun withoutAutoTimerChangeTheSwitchGoesToEdit() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(loadOwifFixture("error404.html"))
        )
        server.enqueue(MockResponse().setBody(loadOwifFixture("autotimer/edit_17.xml")))
        val tatort = autoTimer17(1)

        val switched = api.saveAutoTimer(
            AutoTimerWrite.Change(tatort, tatort.settings.copy(enabled = false))
        )

        assertEquals("3", switched.value?.id)
        assertRequest("/autotimer/change", "id" to "1", "enabled" to "0")
        assertRequest(
            "/autotimer/edit",
            "id" to "1",
            "match" to "Tatort",
            "name" to "Tatort",
            "enabled" to "0",
            "always_zap" to "1"
        )
    }

    @Test
    fun theBouquetEditorListsHiddenServices() = runBlocking {
        answer("getservices_hidden.json")

        val services = api.bouquetEditorServices(FAVOURITES).value!!

        assertRequest("/api/getservices", "sRef" to FAVOURITES, "hidden" to "1")
        assertEquals(
            listOf(DAS_ERSTE, "1:512:19:2B70:3F3:1:C00000:0:0:0:", ZDF),
            services.map { it.reference }
        )
    }

    @Test
    fun satellitesComeFromGetSatellitesByServiceType() = runBlocking {
        answer("getsatellites.json")
        answer("getsatellites.json")

        val tv = api.bouquetEditorSatellites(BouquetMode.Tv).value!!
        api.bouquetEditorSatellites(BouquetMode.Radio)

        assertRequest("/api/getsatellites", "stype" to "tv")
        assertRequest("/api/getsatellites", "stype" to "radio")
        assertEquals(
            listOf(
                "Astra 1KR/1L/1M/1N (19.2E) - Services",
                "Astra 1KR/1L/1M/1N (19.2E) - New"
            ),
            tv.map { it.name }
        )
        assertTrue(tv.first().reference.startsWith("1:7:1:0:0:0:C00000:0:0:0:(satellitePosition"))
        assertTrue(
            tv.first().reference.endsWith("ORDER BY name:Astra 1KR/1L/1M/1N (19.2E) - Services")
        )
    }

    @Test
    fun bouquetEditsSendTheDreamboxParametersToTheApi() = runBlocking {
        repeat(9) { answer("bouqueteditor_addbouquet.json") }

        api.addBouquet(BouquetMode.Radio, "News")
        api.removeBouquet(BouquetMode.Tv, FAVOURITES)
        api.moveBouquet(BouquetMode.Tv, FAVOURITES, 2)
        api.renameBouquet(BouquetMode.Tv, FAVOURITES, "Favs")
        api.addServiceToBouquet(FAVOURITES, ZDF)
        api.removeBouquetService(FAVOURITES, ZDF)
        api.moveBouquetService(BouquetMode.Tv, FAVOURITES, ZDF, 0)
        api.renameBouquetService(FAVOURITES, ZDF, DAS_ERSTE, "Zwei")
        api.addBouquetMarker(FAVOURITES, "Sport", "")

        val bq = "/bouqueteditor/api"
        assertRequest("$bq/addbouquet", "name" to "News", "mode" to "1")
        assertRequest("$bq/removebouquet", "sBouquetRef" to FAVOURITES, "mode" to "0")
        assertRequest(
            "$bq/movebouquet",
            "sBouquetRef" to FAVOURITES,
            "mode" to "0",
            "position" to "2"
        )
        assertRequest("$bq/renameservice", "sRef" to FAVOURITES, "mode" to "0", "newName" to "Favs")
        assertRequest(
            "$bq/addservicetobouquet",
            "sBouquetRef" to FAVOURITES,
            "sRef" to ZDF,
            "sRefBefore" to ""
        )
        assertRequest("$bq/removeservice", "sBouquetRef" to FAVOURITES, "sRef" to ZDF)
        assertRequest(
            "$bq/moveservice",
            "sBouquetRef" to FAVOURITES,
            "sRef" to ZDF,
            "position" to "0",
            "mode" to "0"
        )
        assertRequest(
            "$bq/renameservice",
            "sBouquetRef" to FAVOURITES,
            "sRef" to ZDF,
            "sRefBefore" to DAS_ERSTE,
            "newName" to "Zwei"
        )
        assertRequest(
            "$bq/addmarkertobouquet",
            "sBouquetRef" to FAVOURITES,
            "Name" to "Sport",
            "sRefBefore" to ""
        )
    }

    @Test
    fun aBouquetEditAnswersWithItsResultPair() = runBlocking {
        answer("bouqueteditor_addbouquet.json")
        answer("bouqueteditor_addservice_duplicate.json")
        answer("bouqueteditor_backup.json")
        server.enqueue(MockResponse().setBody(loadOwifFixture("error404.html")))

        val added = api.addBouquet(BouquetMode.Tv, "News & Sport")
        val duplicate = api.addServiceToBouquet(FAVOURITES, SKY)
        val backup = api.backupBouquets("dreamdroid_1700000000")
        val html = api.removeBouquet(BouquetMode.Tv, FAVOURITES)

        assertEquals(SimpleResult("True", "Bouquet News & Sport (TV) created."), added.value)
        assertNull(added.error)
        val refusal = "Service Sky <HD> & Co already exists in bouquet Favourites (TV)."
        assertEquals(SimpleResult("False", refusal), duplicate.value)
        assertEquals(EnigmaFailure.BoxRejected(refusal), duplicate.error?.failure)
        assertEquals("dreamdroid_1700000000.tar", backup.value?.stateText)
        assertNull(html.value)
        assertEquals(EnigmaFailure.Parse, html.error?.failure)
        assertRequest("/bouqueteditor/api/addbouquet", "name" to "News & Sport", "mode" to "0")
        assertRequest(
            "/bouqueteditor/api/addservicetobouquet",
            "sBouquetRef" to FAVOURITES,
            "sRef" to SKY,
            "sRefBefore" to ""
        )
        assertRequest("/bouqueteditor/api/backup", "Filename" to "dreamdroid_1700000000")
    }

    private fun autoTimer17(id: Int): AutoTimer =
        AutoTimerListParser.parse(loadOwifFixture("autotimer/list_17.xml"))!!.entries
            .filterIsInstance<AutoTimerEntry.Readable>()
            .single { it.id == AutoTimerId(id) }
            .autoTimer

    private fun forbidden() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setStatus("HTTP/1.1 403 Forbidden")
                .setBody(loadOwifFixture("error403.html"))
        )
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
