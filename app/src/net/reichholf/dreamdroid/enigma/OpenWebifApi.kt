package net.reichholf.dreamdroid.enigma

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.enigma.openwebif.OwifCurrent
import net.reichholf.dreamdroid.enigma.openwebif.OwifDeviceInfo
import net.reichholf.dreamdroid.enigma.openwebif.OwifEvents
import net.reichholf.dreamdroid.enigma.openwebif.OwifLocations
import net.reichholf.dreamdroid.enigma.openwebif.OwifMovies
import net.reichholf.dreamdroid.enigma.openwebif.OwifResult
import net.reichholf.dreamdroid.enigma.openwebif.OwifServices
import net.reichholf.dreamdroid.enigma.openwebif.OwifSignal
import net.reichholf.dreamdroid.enigma.openwebif.OwifTags
import net.reichholf.dreamdroid.enigma.openwebif.OwifTimers
import net.reichholf.dreamdroid.enigma.openwebif.owifJson
import net.reichholf.dreamdroid.enigma.openwebif.toCurrentService
import net.reichholf.dreamdroid.enigma.openwebif.toDeviceInfo
import net.reichholf.dreamdroid.enigma.openwebif.toEvents
import net.reichholf.dreamdroid.enigma.openwebif.toEventsAt
import net.reichholf.dreamdroid.enigma.openwebif.toMovies
import net.reichholf.dreamdroid.enigma.openwebif.toNowNext
import net.reichholf.dreamdroid.enigma.openwebif.toServices
import net.reichholf.dreamdroid.enigma.openwebif.toSignal
import net.reichholf.dreamdroid.enigma.openwebif.toSimpleResult
import net.reichholf.dreamdroid.enigma.openwebif.toTimers
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * [ReceiverApi] over OpenWebif's `/api` JSON (docs/openwebif.md §2.2), on one [EnigmaHttp].
 * Not built by [ReceiverApiFactory] yet. Source citations are E2OpenPlugins/e2openplugin-OpenWebif
 * at commit e46534f, under plugin/controllers.
 *
 * Every answer is one JSON object (base.py:221-224). A body that is not one is a
 * [EnigmaFailure.Parse]; a handler that returns nothing or throws answers with an HTML 404 or 500
 * (base.py:106-117,211-213), which [EnigmaHttp] reports as [EnigmaFailure.Http]. `result: false`
 * with a `message`, as for a missing parameter (web.py:81-96), is [EnigmaFailure.BoxRejected].
 */
class OpenWebifApi(private val http: EnigmaHttp) : ReceiverApi {
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
        when (val fetched = http.fetch(path, params.toList())) {
            is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
            is EnigmaHttpResult.Success -> decode(fetched.text, deserializer, map)
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
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) {
        when (val fetched = http.fetch(path, params)) {
            is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)

            is EnigmaHttpResult.Success -> {
                val result = try {
                    (owifJson.parseToJsonElement(fetched.text) as? JsonObject)
                        ?.let { owifJson.decodeFromJsonElement(OwifResult.serializer(), it) }
                        ?.toSimpleResult()
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
                when {
                    result == null -> failure(EnigmaFailure.Parse)

                    result.state == Python.FALSE -> EnigmaResponse(
                        result,
                        EnigmaHttpError(EnigmaFailure.BoxRejected(result.stateText.orEmpty()))
                    )

                    else -> EnigmaResponse(result)
                }
            }
        }
    }

    override suspend fun screenshot(): EnigmaResponse<ByteArray> = notYet()

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
     * Port 8001 is enigma2's own stream server, the same on every image; the encoder's RTSP
     * stream is a Dreambox thing a profile can still ask for (docs/openwebif.md §1.1 item 13).
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
            when (val fetched = http.downloadToFile(URIStore.FILE, fileParams(path), destination)) {
                is EnigmaHttpResult.Failure -> fetched.error

                is EnigmaHttpResult.Success ->
                    if (fetched.headers["Content-Disposition"] != null) {
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

    override suspend fun setVolume(command: VolumeCommand): EnigmaResponse<Volume> = notYet()

    override suspend fun setPowerState(command: PowerCommand): EnigmaResponse<PowerState> = notYet()

    override suspend fun sleepTimer(): EnigmaResponse<SleepTimer> = notYet()

    override suspend fun setSleepTimer(
        minutes: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer> = notYet()

    override suspend fun hasPlugin(plugin: ReceiverPlugin): EnigmaResponse<Boolean> = notYet()

    override suspend fun autoTimers(): EnigmaResponse<AutoTimerList> = notYet()

    override suspend fun zap(reference: String): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longPress: Boolean
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult> = notYet()

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

    override suspend fun bouquetEditorSatellites(mode: BouquetMode): EnigmaResponse<List<Service>> =
        notYet()

    override suspend fun addBouquet(mode: BouquetMode, name: String): EnigmaResponse<SimpleResult> =
        notYet()

    override suspend fun removeBouquet(
        mode: BouquetMode,
        bouquetRef: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun moveBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun renameBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun addServiceToBouquet(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun removeBouquetService(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun moveBouquetService(
        mode: BouquetMode,
        bouquetRef: String,
        serviceRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun renameBouquetService(
        bouquetRef: String,
        serviceRef: String,
        beforeRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun addBouquetMarker(
        bouquetRef: String,
        name: String,
        beforeRef: String
    ): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun backupBouquets(fileName: String): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun testAutoTimer(id: AutoTimerId): EnigmaResponse<PreviewOutcome> = notYet()

    override suspend fun saveAutoTimer(write: AutoTimerWrite): EnigmaResponse<SimpleResult> =
        notYet()

    override suspend fun removeAutoTimer(id: AutoTimerId): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun runAutoTimers(): EnigmaResponse<SimpleResult> = notYet()

    /** Unreachable until the factory builds this client (phase 3d). */
    private fun notYet(): Nothing = throw NotImplementedError("phase 3c/4")

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
    timer.autoAdjust?.let { NameValuePair("autoadjust", it) },
    timer.vpsEnabled?.let { NameValuePair("vpsplugin_enabled", it) },
    timer.vpsOverwrite?.let { NameValuePair("vpsplugin_overwrite", it) },
    timer.vpsTime?.let { NameValuePair("vpsplugin_time", it) }
)

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

private const val HEX = 16

private const val ASCII_END = 0x80
