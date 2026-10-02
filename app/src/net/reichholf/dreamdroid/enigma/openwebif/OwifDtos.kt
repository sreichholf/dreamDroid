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
