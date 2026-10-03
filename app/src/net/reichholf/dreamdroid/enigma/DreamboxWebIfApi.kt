package net.reichholf.dreamdroid.enigma

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPluginApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * [ReceiverApi] over the Dreambox web interface (`/web` XML) and the WebBouquetEditor and
 * AutoTimer plugins, on one [EnigmaHttp]. Built by [ReceiverApiFactory]. Stream and file URLs
 * come from the profile of [http]; [capabilities] are that profile's when the client was built.
 */
class DreamboxWebIfApi(private val http: EnigmaHttp, private val capabilities: WebIfCapabilities) :
    ReceiverApi {
    private val autoTimer = AutoTimerPluginApi(http::fetch)

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
            if (capabilities.nowNext) {
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

    override suspend fun currentService(): EnigmaResponse<CurrentService> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.CURRENT).mapParsed { xml ->
                CurrentServiceParser.parse(xml)
            }
        }

    override suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.DEVICE_INFO).mapParsed { xml ->
            DeviceInfoParser.parse(xml)
        }
    }

    override suspend fun signal(): EnigmaResponse<Signal> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.SIGNAL).mapParsed { xml ->
            SignalParser.parse(xml)
        }
    }

    /**
     * `/grab` as a JPEG, which the box writes to a timestamped file under `/tmp` first. When
     * `/grab` answers with something that is not a JPEG or PNG (an empty body on Dreambox Two
     * with Gemini Project, whose grab binary does not know the box), asks `/screenshot` for
     * video and OSD. A body that is still not an image reads as no value.
     */
    override suspend fun screenshot(): EnigmaResponse<ByteArray> = withContext(Dispatchers.IO) {
        val grabParams = listOf(
            NameValuePair("format", "jpg"),
            NameValuePair("filename", "/tmp/dreamDroid-${System.currentTimeMillis() / 1000}")
        )
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

    override suspend fun timers(): EnigmaResponse<List<Timer>> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.TIMER_LIST).mapParsed { xml ->
            TimerParser.parse(xml)
        }
    }

    /** `tag` carries [tags] joined by spaces; `dirname` is left out for the default location. */
    override suspend fun movies(location: String, tags: List<String>): EnigmaResponse<List<Movie>> {
        val params = buildList {
            if (location.isNotEmpty()) {
                add(NameValuePair("dirname", location))
            }
            if (tags.isNotEmpty()) {
                add(NameValuePair("tag", tags.joinToString(" ")))
            }
        }
        return withContext(Dispatchers.IO) {
            http.fetch(URIStore.MOVIES, params).mapParsed { xml ->
                MovieParser.parse(xml)
            }
        }
    }

    override suspend fun locations(): EnigmaResponse<List<String>> =
        fetchStrings(URIStore.LOCATIONS, "e2location")

    override suspend fun tags(): EnigmaResponse<List<String>> = fetchStrings(URIStore.TAGS, "e2tag")

    private suspend fun fetchStrings(uri: String, itemTag: String): EnigmaResponse<List<String>> =
        withContext(Dispatchers.IO) {
            http.fetch(uri).mapParsed { xml -> StringListParser.parse(xml, itemTag) }
        }

    /** The encoder's RTSP stream, or the stream port's HTTP stream, as the profile sets. */
    override fun liveStreamUrl(serviceRef: String): String =
        EnigmaUrls.stream(http.profile, serviceRef)

    /**
     * `/file` on the file port; a `1:` recording goes through the encoder when the profile
     * streams through one.
     */
    override fun recordingStreamUrl(movie: Movie): String =
        EnigmaUrls.fileStream(http.profile, movie.reference, movie.fileName)

    /** `/file` on the web interface's own scheme and port. */
    override fun recordingFileUrl(path: String): String =
        EnigmaUrls.page(http.profile, URIStore.FILE, fileParams(path))

    override suspend fun downloadRecording(path: String, destination: File): EnigmaHttpError? =
        withContext(Dispatchers.IO) {
            when (val fetched = http.downloadToFile(URIStore.FILE, fileParams(path), destination)) {
                is EnigmaHttpResult.Success -> null
                is EnigmaHttpResult.Failure -> fetched.error
            }
        }

    private fun fileParams(path: String) = listOf(NameValuePair("file", path))

    /** `/web/vol` with `set` `up`, `down` or `mute`. */
    override suspend fun setVolume(command: VolumeCommand): EnigmaResponse<Volume> {
        val set = when (command) {
            VolumeCommand.Up -> "up"
            VolumeCommand.Down -> "down"
            VolumeCommand.Mute -> "mute"
        }
        return withContext(Dispatchers.IO) {
            http.fetch(URIStore.VOLUME, listOf(NameValuePair("set", set))).mapParsed { xml ->
                VolumeParser.parse(xml)
            }
        }
    }

    /** `/web/powerstate` with enigma2's `newstate` code. */
    override suspend fun setPowerState(command: PowerCommand): EnigmaResponse<PowerState> {
        val newState = when (command) {
            PowerCommand.ToggleStandby -> "0"
            PowerCommand.Shutdown -> "1"
            PowerCommand.Reboot -> "2"
            PowerCommand.RestartGui -> "3"
        }
        return withContext(Dispatchers.IO) {
            http.fetch(URIStore.POWERSTATE, listOf(NameValuePair("newstate", newState)))
                .mapParsed { xml -> PowerStateParser.parse(xml) }
        }
    }

    override suspend fun sleepTimer(): EnigmaResponse<SleepTimer> = fetchSleepTimer(emptyList())

    /** `/web/sleeptimer` with `cmd=set`; `enabled` is Python's `True` or `False`. */
    override suspend fun setSleepTimer(
        minutes: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer> = fetchSleepTimer(
        listOf(
            NameValuePair("cmd", "set"),
            NameValuePair("time", minutes),
            NameValuePair("action", action),
            NameValuePair("enabled", if (enabled) Python.TRUE else Python.FALSE)
        )
    )

    private suspend fun fetchSleepTimer(params: List<NameValuePair>): EnigmaResponse<SleepTimer> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.SLEEPTIMER, params).mapParsed { xml ->
                SleepTimerParser.parse(xml)
            }
        }

    /**
     * Whether `/web/external` lists `autotimer`, the plugin's API (`autotimereditor` is its web
     * page), and the `api_version` the plugin registered as its version.
     */
    override suspend fun autoTimerPlugin(): EnigmaResponse<AutoTimerPlugin> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.WEB_EXTERNALS).mapParsed { xml ->
                WebExternalsParser.parse(xml)?.let { externals ->
                    externals["autotimer"]?.let { AutoTimerPlugin.Installed(AutoTimerApi.of(it)) }
                        ?: AutoTimerPlugin.Missing
                }
            }
        }

    /** Whether `/web/external` lists `bouqueteditor`. */
    override suspend fun hasBouquetEditor(): EnigmaResponse<Boolean> = withContext(Dispatchers.IO) {
        http.fetch(URIStore.WEB_EXTERNALS).mapParsed { xml ->
            WebExternalsParser.parse(xml)?.containsKey("bouqueteditor")
        }
    }

    override suspend fun autoTimers(): EnigmaResponse<AutoTimerList> = autoTimer.list()

    // Mutations below: a rejected command has a value and a BoxRejected error.
    override suspend fun zap(reference: String): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.ZAP, listOf(NameValuePair("sRef", reference)))

    /** `rcu` is `standard` or `advanced`; a long press adds `type=long`. */
    override suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longPress: Boolean
    ): EnigmaResponse<SimpleResult> = simpleResult(
        URIStore.REMOTECONTROL,
        listOfNotNull(
            NameValuePair("command", keyCode.toString()),
            NameValuePair("rcu", if (simpleRemote) "standard" else "advanced"),
            if (longPress) NameValuePair("type", "long") else null
        )
    )

    override suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult> = simpleResult(
        URIStore.MESSAGE,
        listOf(
            NameValuePair("text", text),
            NameValuePair("type", type),
            NameValuePair("timeout", timeout)
        )
    )

    override suspend fun playMedia(reference: String): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.MEDIA_PLAYER_PLAY, listOf(NameValuePair("file", reference)))

    override suspend fun deleteMovie(movie: Movie): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.MOVIE_DELETE, listOf(NameValuePair("sRef", movie.reference)))

    override suspend fun addTimerForEvent(event: Event): EnigmaResponse<SimpleResult> =
        simpleResult(
            URIStore.TIMER_ADD_BY_EVENT_ID,
            listOf(
                NameValuePair("sRef", event.serviceReference),
                NameValuePair("eventid", event.eventId)
            )
        )

    /** `/web/timerchange` with `deleteOldOnSave=0`, as the app has always added timers. */
    override suspend fun addTimer(timer: Timer): EnigmaResponse<SimpleResult> = simpleResult(
        URIStore.TIMER_CHANGE,
        timerParams(timer) + NameValuePair("deleteOldOnSave", "0")
    )

    /** `/web/timerchange` with the `*Old` keys of [old] and `deleteOldOnSave=1`. */
    override suspend fun editTimer(old: Timer, new: Timer): EnigmaResponse<SimpleResult> =
        simpleResult(
            URIStore.TIMER_CHANGE,
            timerParams(new) + listOf(
                NameValuePair("channelOld", old.reference),
                NameValuePair("beginOld", old.begin),
                NameValuePair("endOld", old.end),
                NameValuePair("deleteOldOnSave", "1")
            )
        )

    /** The Dreambox has no toggle: [editTimer] with only `disabled` changed. */
    override suspend fun setTimerDisabled(
        timer: Timer,
        disabled: Boolean
    ): EnigmaResponse<SimpleResult> =
        editTimer(timer, timer.copy(disabled = if (disabled) "1" else "0"))

    override suspend fun deleteTimer(timer: Timer): EnigmaResponse<SimpleResult> = simpleResult(
        URIStore.TIMER_DELETE,
        listOf(
            NameValuePair("sRef", timer.reference),
            NameValuePair("begin", timer.begin),
            NameValuePair("end", timer.end)
        )
    )

    override suspend fun cleanupTimers(): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.TIMER_CLEANUP)

    // WebBouquetEditor plugin (/bouqueteditor/web).

    /** The same `/web/getservices` the editor has always listed with. */
    override suspend fun bouquetEditorServices(
        containerRef: String
    ): EnigmaResponse<List<Service>> = services(containerRef)

    override suspend fun bouquetEditorSatellites(mode: BouquetMode): EnigmaResponse<List<Service>> =
        withContext(Dispatchers.IO) {
            http.fetch(URIStore.BOUQUET_EDITOR_SATELLITES, listOf(mode.param()))
                .mapParsed { xml -> ServiceParser.parse(xml) }
        }

    override suspend fun addBouquet(mode: BouquetMode, name: String): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.addBouquet(mode, name))

    override suspend fun removeBouquet(
        mode: BouquetMode,
        bouquetRef: String
    ): EnigmaResponse<SimpleResult> = bouquetEdit(BouquetEditorCall.removeBouquet(mode, bouquetRef))

    override suspend fun moveBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.moveBouquet(mode, bouquetRef, position))

    override suspend fun renameBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.renameBouquet(mode, bouquetRef, newName))

    override suspend fun addServiceToBouquet(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.addService(bouquetRef, serviceRef))

    override suspend fun removeBouquetService(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.removeService(bouquetRef, serviceRef))

    override suspend fun moveBouquetService(
        mode: BouquetMode,
        bouquetRef: String,
        serviceRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.moveService(mode, bouquetRef, serviceRef, position))

    override suspend fun renameBouquetService(
        bouquetRef: String,
        serviceRef: String,
        beforeRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.renameService(bouquetRef, serviceRef, beforeRef, newName))

    override suspend fun addBouquetMarker(
        bouquetRef: String,
        name: String,
        beforeRef: String
    ): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.addMarker(bouquetRef, name, beforeRef))

    override suspend fun backupBouquets(fileName: String): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.backup(fileName))

    /** The plugin answers with the `/web` simple result. */
    private suspend fun bouquetEdit(call: BouquetEditorCall): EnigmaResponse<SimpleResult> =
        simpleResult("/bouqueteditor/web/${call.page}?", call.params)

    // AutoTimer plugin (/autotimer), the same requests on every web interface.
    override suspend fun testAutoTimer(id: AutoTimerId): EnigmaResponse<PreviewOutcome> =
        autoTimer.test(id)

    override suspend fun saveAutoTimer(write: AutoTimerWrite): EnigmaResponse<SimpleResult> =
        autoTimer.edit(write)

    override suspend fun removeAutoTimer(id: AutoTimerId): EnigmaResponse<SimpleResult> =
        autoTimer.remove(id)

    override suspend fun runAutoTimers(): EnigmaResponse<SimpleResult> = autoTimer.run()

    private suspend fun simpleResult(
        uri: String,
        params: List<NameValuePair> = emptyList()
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) {
        simpleResultFromFetch(http.fetch(uri, params), SimpleResultParser::parse)
    }

    private fun <T> EnigmaHttpResult.mapParsed(parse: (String) -> T?): EnigmaResponse<T> =
        when (this) {
            is EnigmaHttpResult.Success -> EnigmaResponse(parse(text))
            is EnigmaHttpResult.Failure -> EnigmaResponse(null, error)
        }
}

private const val SECONDS_PER_MINUTE = 60L

/** The fields `/web/timerchange` stores for [timer], in the order the app always sent them. */
private fun timerParams(timer: Timer): List<NameValuePair> = listOf(
    NameValuePair("sRef", timer.reference),
    NameValuePair("begin", timer.begin),
    NameValuePair("end", timer.end),
    NameValuePair("name", timer.name),
    NameValuePair("description", timer.description),
    NameValuePair("dirname", timer.location),
    NameValuePair("tags", timer.tags),
    NameValuePair("eit", timer.eit),
    NameValuePair("disabled", timer.disabled),
    NameValuePair("justplay", timer.justPlay),
    NameValuePair("afterevent", timer.afterEvent),
    NameValuePair("repeated", timer.repeated)
)

private val SCREENSHOT_WEB_PARAMS = listOf(
    NameValuePair("format", "jpg"),
    NameValuePair("osd", "1"),
    NameValuePair("video", "1")
)
