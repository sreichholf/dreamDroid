package net.reichholf.dreamdroid.multiepg

import java.io.Serializable
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.isUnreachableEnigmaFailure
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayText
import net.reichholf.dreamdroid.ui.text.UiText

/** One programme bar in the MultiEPG grid (precomputed unix bounds). */
data class MultiEpgBar(val event: Event, val startSec: Long, val endSec: Long)

/**
 * Which row of the grid: the service and the entry's index among the same service
 * in the bouquet (0 for the first). Serializable because Compose requires lazy item
 * keys to be saveable into a Bundle.
 */
data class MultiEpgChannelKey(val serviceRef: String, val occurrence: Int) : Serializable

/**
 * One channel row with bars already sorted by start. A bouquet may list the same
 * service twice, so the row is identified by [key], not by [serviceRef] alone.
 */
data class MultiEpgChannel(
    val serviceRef: String,
    val serviceName: String,
    val bars: List<MultiEpgBar>,
    /** 0 for the first row of [serviceRef], 1 for the second, and so on. */
    val occurrence: Int
) {
    val key: MultiEpgChannelKey
        get() = MultiEpgChannelKey(serviceRef, occurrence)
}

/**
 * Playable bouquet members for MultiEPG rows. Markers and directories stay out
 * of the grid; the remaining order is the bouquet order from `/web/getservices`.
 */
fun playableMultiEpgRoster(services: List<Service>): List<Service> {
    val out = ArrayList<Service>(services.size)
    for (service in services) {
        val ref = service.reference.trim()
        if (ref.isEmpty()) continue
        if (!isMultiEpgChannel(ref)) continue
        out.add(
            if (ref == service.reference && service.name.isNotBlank()) {
                service
            } else {
                Service(ref, service.name.trim().ifBlank { ref })
            }
        )
    }
    return out
}

/** Result of `/web/getservices` for the painted bouquet roster. */
data class MultiEpgRosterFetch(
    val services: List<Service> = emptyList(),
    val error: Throwable? = null
)

/**
 * Keep the last-good playable roster when getservices fails. A successful empty
 * list still replaces the roster (the bouquet really has no playable rows).
 */
fun applyBouquetRoster(previous: List<Service>, fetch: MultiEpgRosterFetch): AppliedBouquetRoster {
    val error = fetch.error
    if (error != null) {
        return AppliedBouquetRoster(
            roster = previous,
            errorMessage = if (error.isUnreachableEnigmaFailure()) {
                null
            } else {
                error.toEnigmaDisplayText()
            }
        )
    }
    return AppliedBouquetRoster(
        roster = playableMultiEpgRoster(fetch.services),
        errorMessage = null
    )
}

data class AppliedBouquetRoster(val roster: List<Service>, val errorMessage: UiText?)

/**
 * Build channel rows from a flat event list. Keeps work off composition —
 * call from a background dispatcher after [MultiEpgSync.ensureChunk].
 *
 * When [roster] is non-empty, every playable bouquet service keeps its own row even
 * if the loaded windows have no events for it, and a service the bouquet lists more
 * than once keeps one row per occurrence, all showing that service's bars. [previous]
 * reuses bar/channel instances so Compose can skip visible nodes while an off-screen
 * chunk is merged in. The previous list is returned as-is when nothing changed.
 */
fun buildMultiEpgChannels(
    events: List<Event>,
    previous: List<MultiEpgChannel> = emptyList(),
    roster: List<Service> = emptyList()
): List<MultiEpgChannel> {
    val playable = if (roster.isEmpty()) {
        emptyList()
    } else {
        playableMultiEpgRoster(roster)
    }
    if (events.isEmpty() && playable.isEmpty()) return emptyList()
    val previousChannels = HashMap<MultiEpgChannelKey, MultiEpgChannel>(previous.size)
    val previousBars = HashMap<String, MultiEpgBar>(previous.sumOf { it.bars.size })
    for (channel in previous) {
        previousChannels[channel.key] = channel
        for (bar in channel.bars) {
            previousBars[barReuseKey(channel.serviceRef, bar.event.eventId)] = bar
        }
    }
    val byService = LinkedHashMap<String, LinkedHashMap<String, MultiEpgBar>>()
    val names = HashMap<String, String>()
    for (event in events) {
        val ref = event.serviceReference.trim()
        if (ref.isEmpty()) continue
        val start = event.start.toLongOrNull() ?: continue
        val duration = event.duration.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val end = start + duration
        val eventId = event.eventId.trim()
        if (eventId.isEmpty()) continue
        val barsForService = byService.getOrPut(ref) { LinkedHashMap() }
        barsForService[eventId] = reuseOrCreateBar(previousBars, ref, event, start, end)
        if (!names.containsKey(ref)) {
            names[ref] = event.serviceName.ifBlank { ref }
        }
    }
    // Once per service, so every occurrence of a repeated service shares one list.
    val barsByService = byService.mapValues { (_, barsById) -> barList(barsById) }
    val rows = ArrayList<MultiEpgChannel>(playable.size + byService.size)
    if (playable.isNotEmpty()) {
        val occurrences = HashMap<String, Int>()
        for (service in playable) {
            val ref = service.reference
            val occurrence = occurrences[ref] ?: 0
            occurrences[ref] = occurrence + 1
            rows.add(
                channelRow(
                    key = MultiEpgChannelKey(ref, occurrence),
                    serviceName = service.name.ifBlank { names[ref] ?: ref },
                    bars = barsByService[ref].orEmpty(),
                    previousChannels = previousChannels
                )
            )
        }
        for (ref in byService.keys) {
            if (ref in occurrences) continue
            rows.add(
                channelRow(
                    key = MultiEpgChannelKey(ref, 0),
                    serviceName = names[ref] ?: ref,
                    bars = barsByService[ref].orEmpty(),
                    previousChannels = previousChannels
                )
            )
        }
    } else {
        for (ref in byService.keys) {
            rows.add(
                channelRow(
                    key = MultiEpgChannelKey(ref, 0),
                    serviceName = names[ref] ?: ref,
                    bars = barsByService[ref].orEmpty(),
                    previousChannels = previousChannels
                )
            )
        }
    }
    if (rows.size == previous.size && rows.indices.all { rows[it] === previous[it] }) {
        return previous
    }
    return rows
}

private fun barList(barsById: Map<String, MultiEpgBar>?): List<MultiEpgBar> {
    if (barsById == null || barsById.isEmpty()) return emptyList()
    return barsById.values.sortedBy { it.startSec }
}

private fun channelRow(
    key: MultiEpgChannelKey,
    serviceName: String,
    bars: List<MultiEpgBar>,
    previousChannels: Map<MultiEpgChannelKey, MultiEpgChannel>
): MultiEpgChannel {
    val prev = previousChannels[key]
    val barsList: List<MultiEpgBar> = when {
        prev != null && sameBarInstances(prev.bars, bars) -> prev.bars
        bars.isEmpty() -> emptyList()
        else -> bars
    }
    if (prev != null && prev.serviceName == serviceName && prev.bars === barsList) {
        return prev
    }
    return MultiEpgChannel(
        serviceRef = key.serviceRef,
        serviceName = serviceName,
        bars = barsList,
        occurrence = key.occurrence
    )
}

private fun barReuseKey(serviceRef: String, eventId: String): String = "$serviceRef\u0000$eventId"

private fun reuseOrCreateBar(
    previousBars: Map<String, MultiEpgBar>,
    serviceRef: String,
    event: Event,
    startSec: Long,
    endSec: Long
): MultiEpgBar {
    val prev = previousBars[barReuseKey(serviceRef, event.eventId)]
    if (prev != null &&
        prev.startSec == startSec &&
        prev.endSec == endSec &&
        prev.event == event
    ) {
        return prev
    }
    return MultiEpgBar(event = event, startSec = startSec, endSec = endSec)
}

private fun sameBarInstances(previous: List<MultiEpgBar>, next: List<MultiEpgBar>): Boolean {
    if (previous.size != next.size) return false
    for (i in previous.indices) {
        if (previous[i] !== next[i]) return false
    }
    return true
}

/**
 * Bars that intersect [windowStartSec, windowEndSec).
 * [MultiEpgChannel.bars] is sorted by [MultiEpgBar.startSec]; uses a lower-bound
 * binary search then a short backward walk for long-running programmes.
 */
fun List<MultiEpgBar>.overlapping(windowStartSec: Long, windowEndSec: Long): List<MultiEpgBar> {
    if (isEmpty() || windowEndSec <= windowStartSec) return emptyList()
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].startSec < windowStartSec) lo = mid + 1 else hi = mid
    }
    var startIdx = lo
    while (startIdx > 0 && this[startIdx - 1].endSec > windowStartSec) {
        startIdx--
    }
    if (startIdx >= size) return emptyList()
    val out = ArrayList<MultiEpgBar>()
    for (i in startIdx until size) {
        val bar = this[i]
        if (bar.startSec >= windowEndSec) break
        if (bar.endSec > windowStartSec) out.add(bar)
    }
    return out
}
