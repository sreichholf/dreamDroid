package net.reichholf.dreamdroid.enigma.openwebif

import java.net.URLDecoder
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.DeviceFrontend
import net.reichholf.dreamdroid.enigma.DeviceHdd
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.DeviceNic
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.buildEvent
import net.reichholf.dreamdroid.enigma.stripCntrl
import net.reichholf.dreamdroid.helpers.Python

/*
 * OpenWebif `/api` DTOs to domain types. Source citations are E2OpenPlugins/e2openplugin-OpenWebif
 * at commit e46534f, under plugin/controllers.
 */

/**
 * `servicename` is the raw name (models/services.py:615): not HTML-escaped, and the DVB
 * emphasis marks `removeBadChars` drops elsewhere (models/services.py:86-87) are still in it.
 */
internal fun OwifServices.toServices(): List<Service> = services.map { service ->
    Service(service.reference, service.name.replace(BAD_CHARS, "").stripCntrl())
}

/** Every event, in the box's order; rows without an event are dropped. */
internal fun OwifEvents.toEvents(): List<Event> = events.mapNotNull { it.toEvent() }

/**
 * Now and next per service of `/api/epgnownext`, grouped by service reference in the box's
 * order. A service may lack either (web.py:1547 "TODO: fix missing now or next"; enigma2's `X`
 * flag, models/services.py:964, adds a row without an event for the one missing), so rows are
 * not paired by position: an event that has begun by the box's `now_timestamp` is now, a later
 * one is next. Services without any event are left out.
 */
internal fun OwifEvents.toNowNext(): List<ServiceNowNext> {
    val rows = LinkedHashMap<String, ServiceNowNext>()
    for (dto in events) {
        if (dto.sref.isEmpty()) continue
        val event = dto.toEvent() ?: continue
        val row = rows.getOrPut(dto.sref) {
            ServiceNowNext(serviceReference = dto.sref, serviceName = event.serviceName)
        }
        val running = if (dto.now > 0) dto.begin <= dto.now else row.now == null
        rows[dto.sref] = when {
            running && row.now == null -> row.copy(now = event)
            !running && row.next == null -> row.copy(next = event)
            else -> row
        }
    }
    return rows.values.toList()
}

/**
 * Per service, the first event running at [atSec], in the box's order. With `endTime=0` the box
 * should send just that one; an image that sends a range still yields one row per service.
 */
internal fun OwifEvents.toEventsAt(atSec: Long): List<Event> {
    val byService = LinkedHashMap<String, Event>()
    for (dto in events) {
        if (dto.sref in byService || atSec < dto.begin || atSec >= dto.begin + dto.durationSec) {
            continue
        }
        dto.toEvent()?.let { byService[dto.sref] = it }
    }
    return byService.values.toList()
}

/**
 * - The name is always HTML-escaped (`filterName`, models/services.py:162).
 * - The reference is URL-quoted with `%` unsafe (models/services.py:176), so an IPTV ref's
 *   `%3a` arrives as `%253a`; it is decoded once.
 * - Without an event, `now` and `next` are fillers with `begin_timestamp` 0 (web.py:1729-1763,
 *   models/services.py:1020-1031): no event.
 * - While a recording plays, `now` holds the recording's own title and descriptions, unescaped
 *   (web.py:1766-1777).
 * - A service without a provider has `provider` `N/A` (models/services.py:126-128): none.
 */
internal fun OwifCurrent.toCurrentService(): CurrentService {
    val playsFile = now?.sref?.let { ref -> FILE_REF_PREFIXES.any(ref::startsWith) } == true
    return CurrentService(
        service = Service(
            reference = info.ref.percentDecoded(),
            name = info.name.unescapeHtml().stripCntrl().trim(),
            provider = info.provider.trim().takeUnless { it == NOT_AVAILABLE }.orEmpty()
        ),
        now = now?.toEvent(textEscaped = !playsFile),
        next = next?.toEvent()
    )
}

/** The fields `/web/deviceinfo` shows (views/web/deviceinfo.tmpl); `dhcp` as Python prints it. */
internal fun OwifDeviceInfo.toDeviceInfo(): DeviceInfo = DeviceInfo(
    guiVersion = enigmaVersion,
    imageVersion = imageVersion,
    interfaceVersion = webIfVersion,
    frontProcessorVersion = fpVersion,
    deviceName = model,
    frontends = tuners.map { DeviceFrontend(name = it.name, model = it.type) },
    nics = ifaces.map { nic ->
        DeviceNic(
            name = nic.name,
            mac = nic.mac,
            dhcp = if (nic.dhcp) Python.TRUE else Python.FALSE,
            ip = nic.ip,
            gateway = nic.gateway,
            netmask = nic.netmask
        )
    },
    hdds = hdd.map { DeviceHdd(model = it.model, capacity = it.capacity, free = it.free) }
)

/**
 * Labels as `/web/signal` writes them. `snr_db` is a dB string only when the tuner reports
 * dB; otherwise it repeats the percent value as a number (models/info.py:690-694), which is no
 * dB. Null when the box reports nothing, as without a frontend (models/info.py:676-678).
 */
internal fun OwifSignal.toSignal(): Signal? {
    val db = snrDb?.takeIf { it.isString }?.content?.trim().orEmpty()
    val signal = Signal(
        snrDbRaw = db.withUnit("dB"),
        snrRaw = snr.trim().withUnit("%"),
        berRaw = ber.trim(),
        agcRaw = agc.trim().withUnit("%")
    )
    return signal.takeUnless { it.isEmpty() }
}

/**
 * The event, or null for a row without one (`begin_timestamp` 0 or null). In `/api` answers
 * title, descriptions and service name are HTML-escaped: the handlers pass `encode=isJson`
 * (web.py:1460,1496,1600,1654; `filterName` and `convertDesc`, models/services.py:78-111).
 * [textEscaped] false keeps title and descriptions as sent.
 */
private fun OwifEvent.toEvent(textEscaped: Boolean = true): Event? {
    if (begin <= 0) return null
    val text: (String) -> String = if (textEscaped) String::unescapeHtml else { it -> it }
    return buildEvent(
        eventId = id.toString(),
        titleRaw = text(title).trim(),
        eventNameRaw = "",
        startRaw = begin.toString(),
        durationRaw = durationSec.toString(),
        currentTime = if (now > 0) now.toString() else "",
        description = text(shortDescription),
        descriptionExtended = text(longDescription),
        serviceReference = sref.trim(),
        serviceName = sname.unescapeHtml()
    )
}

private fun String.withUnit(unit: String): String = if (isEmpty()) this else "$this $unit"

/** What `removeBadChars` (models/services.py:86-87) removes. */
private val BAD_CHARS = Regex("[\u001a\u0086\u0087\u008a]")

private val FILE_REF_PREFIXES = listOf("1:0:0:0:0:0:0:0:0:0:/", "4097:0:0:0:0:0:0:0:0:0:/")

private val HTML_ESCAPE = Regex("&(amp|lt|gt|quot|#x27);")

/**
 * Undoes Python's `html.escape(s, quote=True)` exactly, in one pass, so `&amp;lt;` becomes
 * `&lt;`. Other entities are left alone: the box never writes them.
 */
private fun String.unescapeHtml(): String = HTML_ESCAPE.replace(this) { match ->
    when (match.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        else -> "'"
    }
}

/** Undoes Python's `quote`, which leaves `+` alone; a malformed escape keeps the input. */
private fun String.percentDecoded(): String = try {
    URLDecoder.decode(replace("+", "%2B"), "UTF-8")
} catch (_: IllegalArgumentException) {
    this
}

/** What `getServiceInfoString` answers for a value the service does not have. */
private const val NOT_AVAILABLE = "N/A"
