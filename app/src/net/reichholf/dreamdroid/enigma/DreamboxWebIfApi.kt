package net.reichholf.dreamdroid.enigma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerListParser
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPreviewParser
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * [ReceiverApi] over the Dreambox web interface (`/web` XML) and the WebBouquetEditor and
 * AutoTimer plugins, on one [EnigmaHttp]. Built by [ReceiverApiFactory].
 */
class DreamboxWebIfApi(private val http: EnigmaHttp) : ReceiverApi {
    override suspend fun services(containerRef: String): EnigmaResponse<List<Service>> =
        fetchServices(NameValuePair("sRef", containerRef))

    override suspend fun epgNowNext(bouquetRef: String): EnigmaResponse<List<ServiceNowNext>> =
        fetchNowNext(NameValuePair("bRef", bouquetRef))

    override suspend fun epgAt(bouquetRef: String, atSec: Long): EnigmaResponse<List<Event>> =
        fetchEvents(
            URIStore.EPG_BOUQUET,
            NameValuePair("bRef", bouquetRef),
            NameValuePair("time", atSec.toString())
        )

    override suspend fun serviceEpg(serviceRef: String): EnigmaResponse<List<Event>> =
        fetchEvents(URIStore.EPG_SERVICE, NameValuePair("sRef", serviceRef))

    /** `endTime` is the window's length in whole minutes, rounded up, at least one. */
    override suspend fun serviceEpg(
        serviceRef: String,
        beginSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>> {
        val minutes = (endSec - beginSec + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
        return fetchEvents(
            URIStore.EPG_SERVICE,
            NameValuePair("sRef", serviceRef),
            NameValuePair("time", beginSec.toString()),
            NameValuePair("endTime", minutes.coerceAtLeast(1).toString())
        )
    }

    /**
     * `time` is the unix start; `endTime` is the **duration in minutes** (eEPGCache's 4th
     * tuple arg, as GraphMultiEPG passes it) despite its name, rounded down, at least one. An
     * absolute unix end overflows on the box and yields no events.
     */
    override suspend fun epgMulti(
        bouquetRef: String,
        startSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>> {
        require(endSec > startSec) { "window end must be after start" }
        val minutes = ((endSec - startSec) / SECONDS_PER_MINUTE).coerceAtLeast(1L)
        return fetchEvents(
            URIStore.EPG_MULTI,
            NameValuePair("bRef", bouquetRef),
            NameValuePair("time", startSec.toString()),
            NameValuePair("endTime", minutes.toString())
        )
    }

    override suspend fun epgSearch(query: String): EnigmaResponse<List<Event>> =
        fetchEvents(URIStore.EPG_SEARCH, NameValuePair("search", query))

    private suspend fun fetchServices(param: NameValuePair): EnigmaResponse<List<Service>> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.SERVICES, listOf(param)).mapParsed { xml ->
                ServiceParser.parse(xml)
            }
        }

    private suspend fun fetchEvents(
        uri: String,
        vararg params: NameValuePair
    ): EnigmaResponse<List<Event>> = withContext(Dispatchers.IO) {
        http.fetch(uri, params.toList()).mapParsed { xml -> EventParser.parse(xml) }
    }

    /**
     * `/web/epgnownext`, or `/web/epgnow` where the receiver lacks it: that one is a flat
     * event list, one row per event with no next.
     */
    private suspend fun fetchNowNext(param: NameValuePair): EnigmaResponse<List<ServiceNowNext>> =
        withContext(Dispatchers.IO) {
            if (DreamDroid.featureNowNext()) {
                http.fetch(URIStore.EPG_NOWNEXT, listOf(param)).mapParsed { xml ->
                    EpgNowNextParser.parse(xml)
                }
            } else {
                http.fetch(URIStore.EPG_NOW, listOf(param)).mapParsed { xml ->
                    EventParser.parse(xml).map { event ->
                        ServiceNowNext(
                            serviceReference = event.serviceReference,
                            serviceName = event.serviceName,
                            now = event,
                            next = null
                        )
                    }
                }
            }
        }

    override suspend fun getCurrent(): EnigmaResponse<CurrentService> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.CURRENT).mapParsed { xml ->
                CurrentServiceParser.parse(xml)
            }
        }

    override suspend fun getDeviceInfo(): EnigmaResponse<DeviceInfo> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.DEVICE_INFO).mapParsed { xml ->
            DeviceInfoParser.parse(xml)
        }
    }

    override suspend fun getSignal(): EnigmaResponse<Signal> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.SIGNAL).mapParsed { xml ->
            SignalParser.parse(xml)
        }
    }

    /**
     * Image bytes of `/grab` with [grabParams]. When `/grab` answers with something that is not
     * a JPEG or PNG (an empty body on Dreambox Two with Gemini Project, whose grab binary does
     * not know the box), asks `/screenshot` for video and OSD. A body that is still not an image
     * reads as no value.
     */
    override suspend fun getScreenshot(grabParams: List<NameValuePair>): EnigmaResponse<ByteArray> =
        withContext(Dispatchers.IO) {
            val grab = fetchImage(URIStore.SCREENSHOT, grabParams)
            if (grab.value != null || grab.error != null) return@withContext grab
            ensureActive()
            val web = fetchImage(URIStore.SCREENSHOT_WEB, SCREENSHOT_WEB_PARAMS)
            if (web.value != null) web else grab
        }

    private fun fetchImage(uri: String, params: List<NameValuePair>): EnigmaResponse<ByteArray> =
        when (val result = http.fetch(uri, ArrayList(params))) {
            is EnigmaHttpResult.Success ->
                EnigmaResponse(result.bytes.takeIf(::looksLikeScreenshotImage))

            is EnigmaHttpResult.Failure -> EnigmaResponse(null, result.error)
        }

    override suspend fun getTimers(): EnigmaResponse<List<Timer>> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.TIMER_LIST).mapParsed { xml ->
            TimerParser.parse(xml)
        }
    }

    override suspend fun getMovies(params: List<NameValuePair>): EnigmaResponse<List<Movie>> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.MOVIES, ArrayList(params)).mapParsed { xml ->
                MovieParser.parse(xml)
            }
        }

    override suspend fun setVolume(params: List<NameValuePair>): EnigmaResponse<Volume> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.VOLUME, params).mapParsed { xml ->
                VolumeParser.parse(xml)
            }
        }

    override suspend fun setPowerState(params: List<NameValuePair>): EnigmaResponse<PowerState> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.POWERSTATE, params).mapParsed { xml ->
                PowerStateParser.parse(xml)
            }
        }

    override suspend fun sleepTimer(params: List<NameValuePair>): EnigmaResponse<SleepTimer> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.SLEEPTIMER, params).mapParsed { xml ->
                SleepTimerParser.parse(xml)
            }
        }

    /** The `e2path` of each web interface plugin (`/web/external`). */
    override suspend fun getWebExternals(): EnigmaResponse<List<String>> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.WEB_EXTERNALS).mapParsed { xml ->
                StringListParser.parse(xml, "e2path")
            }
        }

    /** Satellite roots of the WebBouquetEditor plugin for `mode` (0 TV, 1 radio). */
    override suspend fun getBouquetEditorSatellites(
        params: List<NameValuePair>
    ): EnigmaResponse<List<Service>> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.BOUQUET_EDITOR_SATELLITES, params).mapParsed { xml ->
            ServiceParser.parse(xml)
        }
    }

    /**
     * The AutoTimer plugin's list. A config the box cannot load comes back as a simple result;
     * its text becomes a [EnigmaFailure.BoxRejected].
     */
    override suspend fun getAutoTimers(): EnigmaResponse<AutoTimerList> =
        withContext(Dispatchers.IO) {
            when (val fetched = http.fetch(URIStore.AUTOTIMER_LIST)) {
                is EnigmaHttpResult.Success -> AutoTimerListParser.parse(fetched.text)
                    ?.let { EnigmaResponse(it) }
                    ?: EnigmaResponse(null, rejection(fetched.text))

                is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
            }
        }

    // Mutations below: a rejected command has a value and a BoxRejected error.
    override suspend fun zap(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.ZAP, params)

    override suspend fun remoteCommand(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.REMOTECONTROL, params)

    override suspend fun sendMessage(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.MESSAGE, params)

    override suspend fun playMedia(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.MEDIA_PLAYER_PLAY, params)

    override suspend fun deleteMovie(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.MOVIE_DELETE, params)

    override suspend fun addTimerByEventId(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.TIMER_ADD_BY_EVENT_ID, params)

    override suspend fun changeTimer(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.TIMER_CHANGE, params)

    override suspend fun deleteTimer(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.TIMER_DELETE, params)

    override suspend fun cleanupTimers(): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.TIMER_CLEANUP)

    // WebBouquetEditor plugin (/bouqueteditor). The box applies each edit immediately.
    override suspend fun addBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.BOUQUET_EDITOR_ADD_BOUQUET, params)

    override suspend fun removeBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.BOUQUET_EDITOR_REMOVE_BOUQUET, params)

    override suspend fun moveBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.BOUQUET_EDITOR_MOVE_BOUQUET, params)

    override suspend fun addServiceToBouquet(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.BOUQUET_EDITOR_ADD_SERVICE, params)

    override suspend fun removeBouquetService(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.BOUQUET_EDITOR_REMOVE_SERVICE, params)

    override suspend fun moveBouquetService(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.BOUQUET_EDITOR_MOVE_SERVICE, params)

    override suspend fun renameBouquetEntry(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.BOUQUET_EDITOR_RENAME_SERVICE, params)

    override suspend fun addBouquetMarker(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.BOUQUET_EDITOR_ADD_MARKER, params)

    override suspend fun backupBouquets(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.BOUQUET_EDITOR_BACKUP, params)

    /** What the AutoTimer [id] would record now; the plugin skips disabled ones. */
    override suspend fun testAutoTimer(id: Int): EnigmaResponse<PreviewOutcome> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.AUTOTIMER_TEST, listOf(NameValuePair("id", id.toString())))
                .mapParsed { xml -> AutoTimerPreviewParser.parse(xml) }
        }

    // AutoTimer plugin (/autotimer). Remove answers True even for an unknown id.
    override suspend fun editAutoTimer(params: List<NameValuePair>): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.AUTOTIMER_EDIT, params)

    override suspend fun removeAutoTimer(
        params: List<NameValuePair>
    ): EnigmaResponse<SimpleResult> = simpleResult(URIStore.AUTOTIMER_REMOVE, params)

    /**
     * Runs all enabled AutoTimers now; the reply is the plugin's summary. The box writes
     * `<ignore />` every 50 s while it searches, which the parser skips.
     */
    override suspend fun runAutoTimers(): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.AUTOTIMER_PARSE)

    private suspend fun simpleResult(
        uri: String,
        params: List<NameValuePair> = emptyList()
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) {
        simpleResultFromFetch(http.fetch(uri, params), SimpleResultParser::parse)
    }

    private fun rejection(xml: String): EnigmaHttpError {
        val text = SimpleResultParser.parse(xml)?.stateText
        return EnigmaHttpError(
            if (text.isNullOrBlank()) EnigmaFailure.Parse else EnigmaFailure.BoxRejected(text)
        )
    }

    private fun <T> EnigmaHttpResult.mapParsed(parse: (String) -> T?): EnigmaResponse<T> =
        when (this) {
            is EnigmaHttpResult.Success -> EnigmaResponse(parse(text))
            is EnigmaHttpResult.Failure -> EnigmaResponse(null, error)
        }
}

private const val SECONDS_PER_MINUTE = 60L

private val SCREENSHOT_WEB_PARAMS = listOf(
    NameValuePair("format", "jpg"),
    NameValuePair("osd", "1"),
    NameValuePair("video", "1")
)
