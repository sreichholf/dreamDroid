package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaServiceFlags

/** One programme bar in the MultiEPG grid (precomputed unix bounds). */
data class MultiEpgBar(
    val event: Event,
    val startSec: Long,
    val endSec: Long,
)

/** One channel row with bars already sorted by start. */
data class MultiEpgChannel(
    val serviceRef: String,
    val serviceName: String,
    val bars: List<MultiEpgBar>,
)

/**
 * Playable bouquet members for MultiEPG rows. Markers and directories stay out
 * of the grid; the remaining order is the bouquet order from `/web/getservices`.
 */
fun playableMultiEpgRoster(services: List<Service>): List<Service> {
    val out = ArrayList<Service>(services.size)
    for (service in services) {
        val ref = service.reference.trim()
        if (ref.isEmpty()) continue
        if (EnigmaServiceFlags.isMarker(ref) || EnigmaServiceFlags.isDirectory(ref)) {
            continue
        }
        out.add(
            if (ref == service.reference && service.name.isNotBlank()) {
                service
            } else {
                Service(ref, service.name.trim().ifBlank { ref })
            },
        )
    }
    return out
}

/**
 * Build channel rows from a flat event list. Keeps work off composition —
 * call from a background dispatcher after [MultiEpgSync.ensureChunk].
 *
 * When [roster] is non-empty, every playable bouquet service keeps a row even
 * if the loaded windows have no events for it. [previous] reuses bar/channel
 * instances so Compose can skip visible nodes while an off-screen chunk is
 * merged in. The previous list is returned as-is when nothing changed.
 */
fun buildMultiEpgChannels(
    events: List<Event>,
    previous: List<MultiEpgChannel> = emptyList(),
    roster: List<Service> = emptyList(),
): List<MultiEpgChannel> {
    val playable = if (roster.isEmpty()) {
        emptyList()
    } else {
        playableMultiEpgRoster(roster)
    }
    if (events.isEmpty() && playable.isEmpty()) return emptyList()
    val previousChannels = HashMap<String, MultiEpgChannel>(previous.size)
    val previousBars = HashMap<String, MultiEpgBar>(previous.sumOf { it.bars.size })
    for (channel in previous) {
        previousChannels[channel.serviceRef] = channel
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
    val out = ArrayList<MultiEpgChannel>(maxOf(playable.size, byService.size))
    if (playable.isNotEmpty()) {
        for (service in playable) {
            val ref = service.reference
            val bars = barList(byService.remove(ref))
            val name = service.name.ifBlank { names[ref] ?: ref }
            out.add(channelRow(ref, name, bars, previousChannels))
        }
        for ((ref, barsById) in byService) {
            out.add(channelRow(ref, names[ref] ?: ref, barList(barsById), previousChannels))
        }
    } else {
        for ((ref, barsById) in byService) {
            out.add(channelRow(ref, names[ref] ?: ref, barList(barsById), previousChannels))
        }
    }
    if (out.size == previous.size && out.indices.all { out[it] === previous[it] }) {
        return previous
    }
    return out
}

private fun barList(barsById: LinkedHashMap<String, MultiEpgBar>?): List<MultiEpgBar> {
    if (barsById == null || barsById.isEmpty()) return emptyList()
    return barsById.values.sortedBy { it.startSec }
}

private fun channelRow(
    serviceRef: String,
    serviceName: String,
    bars: List<MultiEpgBar>,
    previousChannels: Map<String, MultiEpgChannel>,
): MultiEpgChannel {
    val prev = previousChannels[serviceRef]
    val barsList: List<MultiEpgBar> =
        if (prev != null && sameBarInstances(prev.bars, bars)) {
            prev.bars
        } else if (bars.isEmpty()) {
            emptyList()
        } else {
            bars
        }
    if (prev != null && prev.serviceName == serviceName && prev.bars === barsList) {
        return prev
    }
    return MultiEpgChannel(
        serviceRef = serviceRef,
        serviceName = serviceName,
        bars = barsList,
    )
}

private fun barReuseKey(serviceRef: String, eventId: String): String {
    return "$serviceRef\u0000$eventId"
}

private fun reuseOrCreateBar(
    previousBars: Map<String, MultiEpgBar>,
    serviceRef: String,
    event: Event,
    startSec: Long,
    endSec: Long,
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

private fun sameBarInstances(
    previous: List<MultiEpgBar>,
    next: List<MultiEpgBar>,
): Boolean {
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
