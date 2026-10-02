package net.reichholf.dreamdroid.enigma

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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    private fun answer(fixture: String) {
        server.enqueue(MockResponse().setBody(loadOwifFixture(fixture)))
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
    }
}
