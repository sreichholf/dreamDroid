package net.reichholf.dreamdroid.enigma

import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPluginApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.FieldGroup
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.enigma.autotimer.groups
import net.reichholf.dreamdroid.enigma.openwebif.OwifCurrent
import net.reichholf.dreamdroid.enigma.openwebif.OwifDeviceInfo
import net.reichholf.dreamdroid.enigma.openwebif.OwifEvents
import net.reichholf.dreamdroid.enigma.openwebif.OwifLocations
import net.reichholf.dreamdroid.enigma.openwebif.OwifMovies
import net.reichholf.dreamdroid.enigma.openwebif.OwifPowerState
import net.reichholf.dreamdroid.enigma.openwebif.OwifResult
import net.reichholf.dreamdroid.enigma.openwebif.OwifSatellites
import net.reichholf.dreamdroid.enigma.openwebif.OwifServices
import net.reichholf.dreamdroid.enigma.openwebif.OwifSignal
import net.reichholf.dreamdroid.enigma.openwebif.OwifSleepTimer
import net.reichholf.dreamdroid.enigma.openwebif.OwifTags
import net.reichholf.dreamdroid.enigma.openwebif.OwifTimers
import net.reichholf.dreamdroid.enigma.openwebif.OwifVolume
import net.reichholf.dreamdroid.enigma.openwebif.owifJson
import net.reichholf.dreamdroid.enigma.openwebif.toCurrentService
import net.reichholf.dreamdroid.enigma.openwebif.toDeviceInfo
import net.reichholf.dreamdroid.enigma.openwebif.toEvents
import net.reichholf.dreamdroid.enigma.openwebif.toEventsAt
import net.reichholf.dreamdroid.enigma.openwebif.toMovies
import net.reichholf.dreamdroid.enigma.openwebif.toNowNext
import net.reichholf.dreamdroid.enigma.openwebif.toPowerState
import net.reichholf.dreamdroid.enigma.openwebif.toServices
import net.reichholf.dreamdroid.enigma.openwebif.toSignal
import net.reichholf.dreamdroid.enigma.openwebif.toSimpleResult
import net.reichholf.dreamdroid.enigma.openwebif.toSleepTimer
import net.reichholf.dreamdroid.enigma.openwebif.toTimers
import net.reichholf.dreamdroid.enigma.openwebif.toVolume
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * [ReceiverApi] over OpenWebif's `/api` JSON (docs/openwebif.md §2.2), on one [EnigmaHttp].
 * [ReceiverApiFactory] builds it for a profile detected as OpenWebif. Source citations are
 * E2OpenPlugins/e2openplugin-OpenWebif at commit e46534f, under plugin/controllers.
 *
 * Every answer is one JSON object (base.py:221-224). A body that is not one is a
 * [EnigmaFailure.Parse]; a handler that returns nothing or throws answers with an HTML 404 or 500
 * (base.py:106-117,211-213), which [EnigmaHttp] reports as [EnigmaFailure.Http]. `result: false`
 * with a `message`, as for a missing parameter (web.py:81-96), is [EnigmaFailure.BoxRejected].
 * A 403 is [EnigmaFailure.IpRejected].
 */
class OpenWebifApi(private val http: EnigmaHttp) : ReceiverApi {
    private val autoTimer = AutoTimerPluginApi(::fetch)

    /** Without `hidden=1`, so hidden services are left out (models/services.py:606). */
    override suspend fun services(containerRef: String): EnigmaResponse<List<Service>> =
        get("/api/getservices", OwifServices.serializer(), NameValuePair("sRef", containerRef)) {
            it.toServices()
        }

    override suspend fun epgNowNext(bouquetRef: String): EnigmaResponse<List<ServiceNowNext>> =
        get("/api/epgnownext", OwifEvents.serializer(), NameValuePair("bRef", bouquetRef)) {
            it.toNowNext()
        }

    /**
     * `endTime=0` asks for the one event at `time`; without it OpenWebif sends every event to
     * the end of the cache (web.py:1453-1460, models/services.py:897-898).
     */
    override suspend fun epgAt(bouquetRef: String, atSec: Long): EnigmaResponse<List<Event>> = get(
        "/api/epgbouquet",
        OwifEvents.serializer(),
        NameValuePair("bRef", bouquetRef),
        NameValuePair("time", atSec.toString()),
        NameValuePair("endTime", "0")
    ) { it.toEventsAt(atSec) }

    override suspend fun serviceEpg(serviceRef: String): EnigmaResponse<List<Event>> =
        get("/api/epgservice", OwifEvents.serializer(), NameValuePair("sRef", serviceRef)) {
            it.toEvents()
        }

    /** `endTime` is the window's length in whole minutes, rounded up, at least one. */
    override suspend fun serviceEpg(
        serviceRef: String,
        beginSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>> {
        val minutes = (endSec - beginSec + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
        return get(
            "/api/epgservice",
            OwifEvents.serializer(),
            NameValuePair("sRef", serviceRef),
            NameValuePair("time", beginSec.toString()),
            NameValuePair("endTime", minutes.coerceAtLeast(1).toString())
        ) { it.toEvents() }
    }

    /**
     * `endTime` is the window's length in minutes, rounded down, at least one; above 100000 the
     * box reads it as "to the end" (models/services.py:893-894).
     */
    override suspend fun epgMulti(
        bouquetRef: String,
        startSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>> {
        require(endSec > startSec) { "window end must be after start" }
        val minutes = ((endSec - startSec) / SECONDS_PER_MINUTE).coerceAtLeast(1L)
        return get(
            "/api/epgmulti",
            OwifEvents.serializer(),
            NameValuePair("bRef", bouquetRef),
            NameValuePair("time", startSec.toString()),
            NameValuePair("endTime", minutes.toString())
        ) { it.toEvents() }
    }

    override suspend fun epgSearch(query: String): EnigmaResponse<List<Event>> =
        get("/api/epgsearch", OwifEvents.serializer(), NameValuePair("search", query)) {
            it.toEvents()
        }

    override suspend fun currentService(): EnigmaResponse<CurrentService> =
        get("/api/getcurrent", OwifCurrent.serializer()) { it.toCurrentService() }

    override suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> =
        get("/api/deviceinfo", OwifDeviceInfo.serializer()) { it.toDeviceInfo() }

    /** `/api/signal` is served by `P_tunersignal` (base.py:187-190). */
    override suspend fun signal(): EnigmaResponse<Signal> =
        get("/api/signal", OwifSignal.serializer()) { it.toSignal() }

    private suspend fun <D, T> get(
        path: String,
        deserializer: DeserializationStrategy<D>,
        vararg params: NameValuePair,
        map: (D) -> T?
    ): EnigmaResponse<T> = withContext(Dispatchers.IO) {
        answer(fetch(path, params.toList()), deserializer, map)
    }

    private fun <D, T> answer(
        fetched: EnigmaHttpResult,
        deserializer: DeserializationStrategy<D>,
        map: (D) -> T?
    ): EnigmaResponse<T> = when (fetched) {
        is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
        is EnigmaHttpResult.Success -> decode(fetched.text, deserializer, map)
    }

    private fun fetch(path: String, params: List<NameValuePair>): EnigmaHttpResult =
        http.fetch(path, params).ipRejected()

    /**
     * A 403 is [EnigmaFailure.IpRejected]: the plugin's only 403 is the rejected client address
     * (plugin/httpserver.py:386-388). Twisted's static file can also answer 403 for a file it
     * cannot open under `/file` (file.py:81-82; Twisted is not in the tree), which then reads as
     * the rejection too.
     */
    private fun EnigmaHttpResult.ipRejected(): EnigmaHttpResult {
        val failure = (this as? EnigmaHttpResult.Failure)?.error?.failure
        return if (failure is EnigmaFailure.Http &&
            failure.code == HttpURLConnection.HTTP_FORBIDDEN
        ) {
            EnigmaHttpResult.Failure(EnigmaHttpError(EnigmaFailure.IpRejected))
        } else {
            this
        }
    }

    private fun <D, T> decode(
        body: String,
        deserializer: DeserializationStrategy<D>,
        map: (D) -> T?
    ): EnigmaResponse<T> {
        val dto = try {
            val answer = owifJson.parseToJsonElement(body) as? JsonObject
                ?: return failure(EnigmaFailure.Parse)
            rejection(answer)?.let { return failure(it) }
            owifJson.decodeFromJsonElement(deserializer, answer)
        } catch (_: SerializationException) {
            return failure(EnigmaFailure.Parse)
        } catch (_: IllegalArgumentException) {
            return failure(EnigmaFailure.Parse)
        }
        return EnigmaResponse(map(dto))
    }

    private fun rejection(answer: JsonObject): EnigmaFailure.BoxRejected? {
        val result = (answer["result"] as? JsonPrimitive)?.booleanOrNull
        val message = (answer["message"] as? JsonPrimitive)?.contentOrNull
        return if (result == false && !message.isNullOrEmpty()) {
            EnigmaFailure.BoxRejected(message)
        } else {
            null
        }
    }

    private fun <T> failure(failure: EnigmaFailure): EnigmaResponse<T> =
        EnigmaResponse(null, EnigmaHttpError(failure))

    /**
     * A command's `{result, message}`. A refusal keeps its value and carries a
     * [EnigmaFailure.BoxRejected], as [ReceiverApi] promises for mutations.
     */
    private suspend fun command(
        path: String,
        params: List<NameValuePair> = emptyList()
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) { exchange(path, params).first }

    /** [command]'s response, with the answer it came from when the body was one. */
    private fun exchange(
        path: String,
        params: List<NameValuePair>
    ): Pair<EnigmaResponse<SimpleResult>, OwifResult?> {
        val fetched = fetch(path, params)
        if (fetched is EnigmaHttpResult.Failure) {
            return EnigmaResponse<SimpleResult>(null, fetched.error) to null
        }
        val answer = try {
            (owifJson.parseToJsonElement((fetched as EnigmaHttpResult.Success).text) as? JsonObject)
                ?.let { owifJson.decodeFromJsonElement(OwifResult.serializer(), it) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val result = answer?.toSimpleResult()
        val response = when {
            result == null -> failure(EnigmaFailure.Parse)

            result.state == Python.FALSE -> EnigmaResponse(
                result,
                EnigmaHttpError(EnigmaFailure.BoxRejected(result.stateText.orEmpty()))
            )

            else -> EnigmaResponse(result)
        }
        return response to answer
    }

    /**
     * `/grab` as a JPEG of video and OSD (models/grab.py:43-46). It takes no `filename` and has
     * no `/screenshot` to fall back to; a body that is not an image reads as no value.
     */
    override suspend fun screenshot(): EnigmaResponse<ByteArray> = withContext(Dispatchers.IO) {
        when (val fetched = fetch(URIStore.SCREENSHOT, listOf(NameValuePair("format", "jpg")))) {
            is EnigmaHttpResult.Success ->
                EnigmaResponse(fetched.bytes.takeIf(::looksLikeScreenshotImage))

            is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
        }
    }

    override suspend fun timers(): EnigmaResponse<List<Timer>> =
        get("/api/timerlist", OwifTimers.serializer()) { it.toTimers() }

    /**
     * `movielist` takes one `tag` (models/movies.py:121,213-214), so the first of [tags] goes to
     * the box and the list is narrowed to recordings carrying all of them, as the Dreambox does
     * with its space-separated `tag`. `dirname` is sent in `%uXXXX` form; see [owifDirname].
     */
    override suspend fun movies(location: String, tags: List<String>): EnigmaResponse<List<Movie>> {
        val params = buildList {
            if (location.isNotEmpty()) {
                add(NameValuePair("dirname", owifDirname(location)))
            }
            tags.firstOrNull()?.let { add(NameValuePair("tag", it)) }
        }
        return get("/api/movielist", OwifMovies.serializer(), *params.toTypedArray()) { dto ->
            dto.toMovies().filter { movie ->
                val movieTags = movie.tags.split(' ')
                tags.all { it in movieTags }
            }
        }
    }

    override suspend fun locations(): EnigmaResponse<List<String>> =
        get("/api/getlocations", OwifLocations.serializer()) { it.locations }

    /** `gettags` without `sRef` lists the box's tag file (web.py:965-978). */
    override suspend fun tags(): EnigmaResponse<List<String>> =
        get("/api/gettags", OwifTags.serializer()) { it.tags }

    /**
     * By the profile's `StreamMode`: `Direct` is enigma2's own stream server on port 8001, the
     * same on every image. `Transcoding` is the box's transcoding server on the profile's
     * transcode port, 8002 by default, which OpenWebif's own stream links use
     * (models/stream.py:83-90). `Encoder`, the Dreambox's RTSP encoder, a profile can still ask
     * for (docs/openwebif.md §1.1 item 13).
     */
    override fun liveStreamUrl(serviceRef: String): String =
        EnigmaUrls.stream(http.profile, serviceRef)

    /** `/file?file=` downloads the file by default, as on the Dreambox (file.py:50-83). */
    override fun recordingStreamUrl(movie: Movie): String =
        EnigmaUrls.fileStream(http.profile, movie.reference, movie.fileName)

    override fun recordingFileUrl(path: String): String =
        EnigmaUrls.page(http.profile, URIStore.FILE, fileParams(path))

    /**
     * A file comes with `Content-Disposition` (file.py:80-83). A missing file is HTTP 200 with
     * the text "File '…' not found" and no such header (file.py:58-59) where Twisted sends a
     * returned `str` (Python 2); that text is a [EnigmaFailure.BoxRejected] and [destination]
     * is deleted. *Inference:* on Python 3, Twisted answers that `str` with an HTML 500.
     */
    override suspend fun downloadRecording(path: String, destination: File): EnigmaHttpError? =
        withContext(Dispatchers.IO) {
            val fetched = http.downloadToFile(URIStore.FILE, fileParams(path), destination)
            when (val answer = fetched.ipRejected()) {
                is EnigmaHttpResult.Failure -> answer.error

                is EnigmaHttpResult.Success ->
                    if (answer.headers["Content-Disposition"] != null) {
                        null
                    } else {
                        notAFile(destination)
                    }
            }
        }

    private fun notAFile(destination: File): EnigmaHttpError {
        val text = destination.inputStream().use { input ->
            val head = ByteArray(NOT_A_FILE_TEXT_BYTES)
            String(head, 0, input.read(head).coerceAtLeast(0)).trim()
        }
        destination.delete()
        return EnigmaHttpError(
            if (text.isEmpty()) EnigmaFailure.Parse else EnigmaFailure.BoxRejected(text)
        )
    }

    private fun fileParams(path: String) = listOf(NameValuePair("file", path))

    /** `vol` with `set` `up`, `down` or `mute`; the answer has the new level (models/volume.py). */
    override suspend fun setVolume(command: VolumeCommand): EnigmaResponse<Volume> {
        val set = when (command) {
            VolumeCommand.Up -> "up"
            VolumeCommand.Down -> "down"
            VolumeCommand.Mute -> "mute"
        }
        return get("/api/vol", OwifVolume.serializer(), NameValuePair("set", set)) {
            it.toVolume()
        }
    }

    /**
     * `powerstate` with enigma2's `newstate` code. Its answer is the standby state from before
     * the action (models/control.py:208,232-235: `inStandby` is imported first), so a toggle
     * asks again. *Inference:* leaving standby closes the screen on the next main loop turn,
     * which runs before the box serves the second request. Shutdown, reboot and restart take
     * the box down, so their answer is kept rather than asked again.
     */
    override suspend fun setPowerState(command: PowerCommand): EnigmaResponse<PowerState> {
        val newState = when (command) {
            PowerCommand.ToggleStandby -> "0"
            PowerCommand.Shutdown -> "1"
            PowerCommand.Reboot -> "2"
            PowerCommand.RestartGui -> "3"
        }
        val changed = get(
            "/api/powerstate",
            OwifPowerState.serializer(),
            NameValuePair("newstate", newState)
        ) { it.toPowerState() }
        if (command != PowerCommand.ToggleStandby || changed.error != null) {
            return changed
        }
        return get("/api/powerstate", OwifPowerState.serializer()) { it.toPowerState() }
    }

    /**
     * Without `cmd` the box reads the timer. On images whose sleep timer is a power timer, no
     * such timer returns nothing, which is an HTML 404 (models/timers.py:912-935,
     * base.py:210-213): a sleep timer that is off. `minutes` is the configured time on images
     * with an InfoBar sleep timer (models/timers.py:878-900), not the time left.
     */
    override suspend fun sleepTimer(): EnigmaResponse<SleepTimer> = withContext(Dispatchers.IO) {
        val fetched = fetch("/api/sleeptimer", emptyList())
        val failure = (fetched as? EnigmaHttpResult.Failure)?.error?.failure
        if (failure is EnigmaFailure.Http && failure.code == HttpURLConnection.HTTP_NOT_FOUND) {
            EnigmaResponse(SleepTimer(enabled = Python.FALSE))
        } else {
            answer(fetched, OwifSleepTimer.serializer()) { it.toSleepTimer() }
        }
    }

    /**
     * `cmd=set`; `enabled` is Python's `True` or `False`, which the box also accepts
     * (web.py:2117-2122). No `time` is sent for null [minutes]: an empty one is no number.
     *
     * A refusal is the unchanged timer with a message starting `ERROR`, and no `result`
     * (web.py:2124-2136, models/timers.py:944-946): it keeps that timer and is a
     * [EnigmaFailure.BoxRejected]. On images whose sleep timer is a power timer every change
     * fails with "SleepTimer error" (models/timers.py:1027,1069 call the `time` argument).
     */
    override suspend fun setSleepTimer(
        minutes: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer> = withContext(Dispatchers.IO) {
        val params = listOfNotNull(
            NameValuePair("cmd", "set"),
            minutes?.let { NameValuePair("time", it) },
            action?.let { NameValuePair("action", it) },
            NameValuePair("enabled", if (enabled) Python.TRUE else Python.FALSE)
        )
        val answered = answer(fetch("/api/sleeptimer", params), OwifSleepTimer.serializer()) {
            it.toSleepTimer()
        }
        val refusal = answered.value?.text?.takeIf { it.startsWith(SLEEP_TIMER_ERROR) }
        if (refusal != null) {
            EnigmaResponse(answered.value, EnigmaHttpError(EnigmaFailure.BoxRejected(refusal)))
        } else {
            answered
        }
    }

    /** `/autotimer/get`; see [AutoTimerPluginApi.plugin]. */
    override suspend fun autoTimerPlugin(): EnigmaResponse<AutoTimerPlugin> = autoTimer.plugin()

    /**
     * OpenWebif always mounts its own bouquet editor (root.py:76, `BQE.py`, `BouquetEditor.py`),
     * so this asks nothing.
     */
    override suspend fun hasBouquetEditor(): EnigmaResponse<Boolean> = EnigmaResponse(true)

    override suspend fun autoTimers(): EnigmaResponse<AutoTimerList> = autoTimer.list()

    override suspend fun zap(reference: String): EnigmaResponse<SimpleResult> =
        command("/api/zap", listOf(NameValuePair("sRef", reference)))

    /**
     * As on the Dreambox: `rcu` `standard` or `advanced` picks the remote, `type=long` holds the
     * key (web.py:323-358, models/control.py:169-205).
     */
    override suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longPress: Boolean
    ): EnigmaResponse<SimpleResult> = command(
        "/api/remotecontrol",
        listOfNotNull(
            NameValuePair("command", keyCode.toString()),
            NameValuePair("rcu", if (simpleRemote) "standard" else "advanced"),
            if (longPress) NameValuePair("type", "long") else null
        )
    )

    /** `text` and `type` are mandatory; a `timeout` that is no number is none (web.py:723-743). */
    override suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult> = command(
        "/api/message",
        listOf(
            NameValuePair("text", text),
            NameValuePair("type", type),
            NameValuePair("timeout", timeout)
        )
    )

    /** `mediaplayerplay` answers "Mediaplayer not installed" without the plugin. */
    override suspend fun playMedia(reference: String): EnigmaResponse<SimpleResult> =
        command("/api/mediaplayerplay", listOf(NameValuePair("file", reference)))

    override suspend fun deleteMovie(movie: Movie): EnigmaResponse<SimpleResult> =
        command("/api/moviedelete", listOf(NameValuePair("sRef", movie.reference)))

    override suspend fun addTimerForEvent(event: Event): EnigmaResponse<SimpleResult> = command(
        "/api/timeraddbyeventid",
        listOf(
            NameValuePair("sRef", event.serviceReference),
            NameValuePair("eventid", event.eventId)
        )
    )

    /**
     * `timeradd`; `timerchange` wants the old timer's keys (web.py:1264). A new timer carries
     * none of the settings [Timer] keeps for the round trip: `allow_duplicate` is off and
     * auto-adjust follows the box's setting (web.py:1084-1089, models/timers.py:316-322).
     */
    override suspend fun addTimer(timer: Timer): EnigmaResponse<SimpleResult> =
        command("/api/timeradd", timerParams(timer))

    /**
     * `timerchange` edits the timer found by `channelOld`, `beginOld` and `endOld` in place
     * (models/timers.py:395-400); it has no `deleteOldOnSave`. It resets duplicates,
     * auto-adjust and VPS unless they are sent (web.py:985-1011,1084-1089), so [new] carries
     * them from the timer list.
     */
    override suspend fun editTimer(old: Timer, new: Timer): EnigmaResponse<SimpleResult> = command(
        "/api/timerchange",
        timerParams(new) + listOf(
            NameValuePair("channelOld", old.reference),
            NameValuePair("beginOld", old.begin),
            NameValuePair("endOld", old.end)
        )
    )

    /**
     * `timertogglestatus` flips only `disabled` and refuses an enable that conflicts
     * (web.py:1270-1302, models/timers.py:519-561). It toggles, so when the box already had the
     * other state the answer's `disabled` is wrong and the second toggle lands on [disabled].
     */
    override suspend fun setTimerDisabled(
        timer: Timer,
        disabled: Boolean
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) {
        val params = listOf(
            NameValuePair("sRef", timer.reference),
            NameValuePair("begin", timer.begin),
            NameValuePair("end", timer.end)
        )
        val (response, answer) = exchange("/api/timertogglestatus", params)
        val now = answer?.disabled?.trim()?.lowercase()?.let { it == "true" || it == "1" }
        if (response.error == null && now == !disabled) {
            exchange("/api/timertogglestatus", params).first
        } else {
            response
        }
    }

    /**
     * Found by service, begin and end. No `eit`: the box would delete the first timer of the
     * service with that event, which may be another one (models/timers.py:500-504).
     */
    override suspend fun deleteTimer(timer: Timer): EnigmaResponse<SimpleResult> = command(
        "/api/timerdelete",
        listOf(
            NameValuePair("sRef", timer.reference),
            NameValuePair("begin", timer.begin),
            NameValuePair("end", timer.end)
        )
    )

    override suspend fun cleanupTimers(): EnigmaResponse<SimpleResult> =
        command("/api/timercleanup")

    /**
     * `getservices` with `hidden=1`: the editor's positions count hidden services
     * (models/services.py:602-606), and moving or removing must see them.
     */
    override suspend fun bouquetEditorServices(
        containerRef: String
    ): EnigmaResponse<List<Service>> = get(
        "/api/getservices",
        OwifServices.serializer(),
        NameValuePair("sRef", containerRef),
        NameValuePair("hidden", "1")
    ) { it.toServices() }

    /** OpenWebif's bouquet editor has no `satelliteslist`; `getsatellites` (web.py:2203). */
    override suspend fun bouquetEditorSatellites(mode: BouquetMode): EnigmaResponse<List<Service>> {
        val type = when (mode) {
            BouquetMode.Tv -> "tv"
            BouquetMode.Radio -> "radio"
        }
        return get(
            "/api/getsatellites",
            OwifSatellites.serializer(),
            NameValuePair("stype", type)
        ) {
            it.toServices()
        }
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

    /** `backup` answers with the tar's name, `<fileName>.tar` (BouquetEditor.py:586-624). */
    override suspend fun backupBouquets(fileName: String): EnigmaResponse<SimpleResult> =
        bouquetEdit(BouquetEditorCall.backup(fileName))

    /**
     * `/bouqueteditor/api/<page>` answers `{"Result": [ok, text]}` (BQE.py:46-48,419-424), the
     * text not escaped. A refusal keeps its value and carries a [EnigmaFailure.BoxRejected].
     */
    private suspend fun bouquetEdit(call: BouquetEditorCall): EnigmaResponse<SimpleResult> =
        withContext(Dispatchers.IO) {
            when (val fetched = fetch("/bouqueteditor/api/${call.page}", call.params)) {
                is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)

                is EnigmaHttpResult.Success -> when (val result = bouquetResult(fetched.text)) {
                    null -> failure(EnigmaFailure.Parse)

                    else -> if (result.state == Python.FALSE) {
                        EnigmaResponse(
                            result,
                            EnigmaHttpError(EnigmaFailure.BoxRejected(result.stateText.orEmpty()))
                        )
                    } else {
                        EnigmaResponse(result)
                    }
                }
            }
        }

    private fun bouquetResult(body: String): SimpleResult? {
        val result = try {
            (owifJson.parseToJsonElement(body) as? JsonObject)?.get("Result") as? JsonArray
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        val ok = (result.getOrNull(0) as? JsonPrimitive)?.booleanOrNull ?: return null
        val text = (result.getOrNull(1) as? JsonPrimitive)?.contentOrNull.orEmpty()
        return SimpleResult(state = if (ok) Python.TRUE else Python.FALSE, stateText = text)
    }

    // AutoTimer plugin (/autotimer): the Dreambox's requests, but the enable switch goes to
    // `/autotimer/change`.
    override suspend fun testAutoTimer(id: AutoTimerId): EnigmaResponse<PreviewOutcome> =
        autoTimer.test(id)

    /**
     * A write that only switches the AutoTimer on or off goes to `/autotimer/change`, which
     * touches nothing else. Where that answers 404 (a plugin without it), it goes to `edit`.
     */
    override suspend fun saveAutoTimer(write: AutoTimerWrite): EnigmaResponse<SimpleResult> {
        if (write is AutoTimerWrite.Change && write.groups == setOf(FieldGroup.Enabled)) {
            val changed = autoTimer.change(write.loaded.id, write.edited.enabled)
            val failure = changed.error?.failure
            if (failure !is EnigmaFailure.Http ||
                failure.code != HttpURLConnection.HTTP_NOT_FOUND
            ) {
                return changed
            }
        }
        return autoTimer.edit(write)
    }

    override suspend fun removeAutoTimer(id: AutoTimerId): EnigmaResponse<SimpleResult> =
        autoTimer.remove(id)

    override suspend fun runAutoTimers(): EnigmaResponse<SimpleResult> = autoTimer.run()

    private companion object {
        const val SECONDS_PER_MINUTE = 60L

        /** More than OpenWebif's "File '…' not found" for any real path. */
        const val NOT_A_FILE_TEXT_BYTES = 1024
    }
}

/**
 * The fields `timeradd` and `timerchange` read (web.py:1047-1182).
 * - `name` must not be empty (web.py:89-93,1198,1264). A timer the user left unnamed is
 *   named after its service, which is what enigma2 names its recording file after; with no
 *   service name either, the box's own "can't be empty" comes back as a rejection.
 * - `repeated` goes through `int()`, so an empty one would be an HTML 500 (web.py:1066).
 * - Empty `tags` are left out: the box would store one empty tag (web.py:1062-1064).
 * - No `eit`: the box reads it only as a number, which a query value never is, and looks the
 *   event up by time instead (web.py:1081-1088).
 * - VPS goes as all three params when the timer's is known ([params]). Without them the box
 *   turns VPS off (web.py:990-996, models/timers.py:416-419).
 */
private fun timerParams(timer: Timer): List<NameValuePair> = listOfNotNull(
    NameValuePair("sRef", timer.reference),
    NameValuePair("begin", timer.begin),
    NameValuePair("end", timer.end),
    NameValuePair("name", timer.name.ifBlank { timer.serviceName }),
    NameValuePair("description", timer.description),
    NameValuePair("dirname", timer.location),
    timer.tags.takeIf { it.isNotBlank() }?.let { NameValuePair("tags", it) },
    NameValuePair("disabled", timer.disabled),
    NameValuePair("justplay", timer.justPlay),
    NameValuePair("afterevent", timer.afterEvent),
    NameValuePair("repeated", timer.repeated.ifBlank { "0" }),
    timer.allowDuplicate?.let { NameValuePair("allow_duplicate", it) },
    timer.autoAdjust?.let { NameValuePair("autoadjust", it) }
) + timer.vps?.params().orEmpty()

/**
 * [path] for `movielist`'s `dirname`. The box decodes that argument as Latin-1 and then turns
 * each `%uXXXX` into its character (models/movies.py:115-118), so UTF-8 bytes come out garbled
 * and the folder is not found. Every character outside ASCII, and `%` itself, goes as `%uXXXX`.
 * A character outside the Basic Multilingual Plane becomes two surrogates the box cannot encode.
 */
private fun owifDirname(path: String): String = buildString {
    for (c in path) {
        if (c.code < ASCII_END && c != '%') {
            append(c)
        } else {
            append("%u").append(c.code.toString(HEX).uppercase().padStart(4, '0'))
        }
    }
}

private const val SLEEP_TIMER_ERROR = "ERROR"

private const val HEX = 16

private const val ASCII_END = 0x80
