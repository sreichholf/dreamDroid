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
import net.reichholf.dreamdroid.enigma.openwebif.OwifServices
import net.reichholf.dreamdroid.enigma.openwebif.OwifSignal
import net.reichholf.dreamdroid.enigma.openwebif.owifJson
import net.reichholf.dreamdroid.enigma.openwebif.toCurrentService
import net.reichholf.dreamdroid.enigma.openwebif.toDeviceInfo
import net.reichholf.dreamdroid.enigma.openwebif.toEvents
import net.reichholf.dreamdroid.enigma.openwebif.toEventsAt
import net.reichholf.dreamdroid.enigma.openwebif.toNowNext
import net.reichholf.dreamdroid.enigma.openwebif.toServices
import net.reichholf.dreamdroid.enigma.openwebif.toSignal
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair

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

    override suspend fun screenshot(): EnigmaResponse<ByteArray> = notYet()

    override suspend fun timers(): EnigmaResponse<List<Timer>> = notYet()

    override suspend fun movies(location: String, tags: List<String>): EnigmaResponse<List<Movie>> =
        notYet()

    override suspend fun locations(): EnigmaResponse<List<String>> = notYet()

    override suspend fun tags(): EnigmaResponse<List<String>> = notYet()

    override fun liveStreamUrl(serviceRef: String): String = notYet()

    override fun recordingStreamUrl(movie: Movie): String = notYet()

    override fun recordingFileUrl(path: String): String = notYet()

    override suspend fun downloadRecording(path: String, destination: File): EnigmaHttpError? =
        notYet()

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

    override suspend fun playMedia(reference: String): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun deleteMovie(movie: Movie): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun addTimerForEvent(event: Event): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun addTimer(timer: Timer): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun editTimer(old: Timer, new: Timer): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun deleteTimer(timer: Timer): EnigmaResponse<SimpleResult> = notYet()

    override suspend fun cleanupTimers(): EnigmaResponse<SimpleResult> = notYet()

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
    private fun notYet(): Nothing = throw NotImplementedError("phase 3b/3c")

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
