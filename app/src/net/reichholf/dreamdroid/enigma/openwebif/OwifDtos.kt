package net.reichholf.dreamdroid.enigma.openwebif

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/*
 * The `/api` JSON of OpenWebif, as the models under plugin/controllers/models of
 * E2OpenPlugins/e2openplugin-OpenWebif (commit e46534f) build it. OpenWebif has no API version,
 * so every field has a default and the parser is lenient. Only the fields the app reads are
 * declared. Mapping to domain types lives in OwifMapping.kt.
 */

/** Unknown keys, quoted numbers and nulls for defaulted fields all parse. */
internal val owifJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** `getServices` (models/services.py:550-631). */
@Serializable
internal data class OwifServices(val services: List<OwifService> = emptyList())

@Serializable
internal data class OwifService(
    @SerialName("servicereference") val reference: String = "",
    @SerialName("servicename") val name: String = ""
)

/**
 * The `events` list of `getBouquetEpg`, `getBouquetNowNextEpg`, `getChannelEpg` and
 * `getSearchEpg` (models/services.py:799-1139).
 */
@Serializable
internal data class OwifEvents(val events: List<OwifEvent> = emptyList())

/**
 * One EPG event. A row with no event (enigma2's `X` placeholder, or OpenWebif's own filler) has
 * [begin] 0 or null.
 */
@Serializable
internal data class OwifEvent(
    val id: Long = 0,
    @SerialName("begin_timestamp") val begin: Long = 0,
    @SerialName("duration_sec") val durationSec: Long = 0,
    @SerialName("now_timestamp") val now: Long = 0,
    val title: String = "",
    @SerialName("shortdesc") val shortDescription: String = "",
    @SerialName("longdesc") val longDescription: String = "",
    val sref: String = "",
    val sname: String = ""
)

/** `P_getcurrent` (web.py:1706-1801). */
@Serializable
internal data class OwifCurrent(
    val info: OwifCurrentInfo = OwifCurrentInfo(),
    val now: OwifEvent? = null,
    val next: OwifEvent? = null
)

/** `getCurrentService` (models/services.py:133-203). */
@Serializable
internal data class OwifCurrentInfo(
    val name: String = "",
    val ref: String = "",
    val provider: String = ""
)

/** `getInfo` (models/info.py:200-582), what `P_deviceinfo` returns (web.py:1421). */
@Serializable
internal data class OwifDeviceInfo(
    @SerialName("enigmaver") val enigmaVersion: String = "",
    @SerialName("imagever") val imageVersion: String = "",
    @SerialName("webifver") val webIfVersion: String = "",
    @SerialName("fp_version") val fpVersion: String = "",
    val model: String = "",
    val tuners: List<OwifTuner> = emptyList(),
    val ifaces: List<OwifInterface> = emptyList(),
    val hdd: List<OwifHdd> = emptyList()
)

@Serializable
internal data class OwifTuner(val name: String = "", val type: String = "")

@Serializable
internal data class OwifInterface(
    val name: String = "",
    val mac: String = "",
    val dhcp: Boolean = false,
    val ip: String = "",
    @SerialName("gw") val gateway: String = "",
    @SerialName("mask") val netmask: String = ""
)

@Serializable
internal data class OwifHdd(
    val model: String = "",
    val capacity: String = "",
    val free: String = ""
)

/**
 * `getFrontendStatus` (models/info.py:661-702). Each value is `""` without a frontend, else a
 * number; [snrDb] is a string only when the tuner reports dB.
 */
@Serializable
internal data class OwifSignal(
    val snr: String = "",
    @SerialName("snr_db") val snrDb: JsonPrimitive? = null,
    val agc: String = "",
    val ber: String = ""
)

/**
 * The answer of a command: zap, remote control, message, timers, movie delete
 * (models/control.py, models/timers.py, models/movies.py, models/message.py,
 * models/mediaplayer.py). [result] is null when the key is missing, which no command does.
 */
@Serializable
internal data class OwifResult(val result: Boolean? = null, val message: String = "")

/** `getTimers` (models/timers.py:78-253). `logentries` is a list of lists and is not read. */
@Serializable
internal data class OwifTimers(val timers: List<OwifTimer> = emptyList())

/** Numbers and Python bools arrive as JSON literals; the lenient parser reads them as text. */
@Serializable
internal data class OwifTimer(
    @SerialName("serviceref") val reference: String = "",
    @SerialName("servicename") val serviceName: String = "",
    val eit: String = "",
    val name: String = "",
    val description: String = "",
    @SerialName("descriptionextended") val descriptionExtended: String = "",
    val disabled: String = "",
    val begin: String = "",
    val end: String = "",
    val duration: String = "",
    @SerialName("startprepare") val startPrepare: String = "",
    @SerialName("justplay") val justPlay: String = "",
    @SerialName("afterevent") val afterEvent: String = "",
    val dirname: String = "",
    val tags: String = "",
    @SerialName("backoff") val backOff: String = "",
    @SerialName("firsttryprepare") val firstTryPrepare: String = "",
    val state: String = "",
    val repeated: String = "",
    @SerialName("dontsave") val dontSave: String = "",
    val cancelled: String = "",
    @SerialName("toggledisabled") val toggleDisabled: String = "",
    val filename: String = "",
    @SerialName("nextactivation") val nextActivation: String = "",
    @SerialName("allow_duplicate") val allowDuplicate: String? = null,
    val autoadjust: String? = null,
    @SerialName("vpsplugin_enabled") val vpsEnabled: String? = null,
    @SerialName("vpsplugin_overwrite") val vpsOverwrite: String? = null,
    @SerialName("vpsplugin_time") val vpsTime: String? = null
)

/** `getMovieList` (models/movies.py:107-321). */
@Serializable
internal data class OwifMovies(val movies: List<OwifMovie> = emptyList())

@Serializable
internal data class OwifMovie(
    @SerialName("serviceref") val reference: String = "",
    @SerialName("eventname") val title: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    @SerialName("servicename") val serviceName: String = "",
    @SerialName("recordingtime") val recordingTime: String = "",
    val length: String = "",
    val tags: String = "",
    val filename: String = "",
    val filesize: String = ""
)

/** `getLocations` (models/locations.py:15-20). */
@Serializable
internal data class OwifLocations(val locations: List<String> = emptyList())

/** `getMovieInfo` without a recording (models/movies.py:753-773). */
@Serializable
internal data class OwifTags(val tags: List<String> = emptyList())
