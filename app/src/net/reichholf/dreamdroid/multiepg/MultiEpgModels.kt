package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event

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
 * Build channel rows from a flat event list. Keeps work off composition —
 * call from a background dispatcher after [MultiEpgSync.ensureChunk].
 *
 * When [previous] is supplied, programme bars whose eventId/times/payload match
 * are the same instances so Compose can skip visible nodes while an off-screen
 * chunk is merged in. Unchanged rows keep their [MultiEpgChannel] instance.
 * The previous list is returned as-is when nothing changed.
 */
fun buildMultiEpgChannels(
    events: List<Event>,
    previous: List<MultiEpgChannel> = emptyList(),
): List<MultiEpgChannel> {
    if (events.isEmpty()) return emptyList()
    val previousChannels = HashMap<String, MultiEpgChannel>(previous.size)
    val previousBars = HashMap<String, MultiEpgBar>(previous.sumOf { it.bars.size })
    for (channel in previous) {
        previousChannels[channel.serviceRef] = channel
        for (bar in channel.bars) {
            previousBars[barReuseKey(channel.serviceRef, bar.event.eventId)] = bar
        }
    }
    val byService = LinkedHashMap<String, MutableList<MultiEpgBar>>()
    val names = HashMap<String, String>()
    for (event in events) {
        val ref = event.serviceReference.trim()
        if (ref.isEmpty()) continue
        val start = event.start.toLongOrNull() ?: continue
        val duration = event.duration.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val end = start + duration
        byService.getOrPut(ref) { ArrayList() }.add(
            reuseOrCreateBar(previousBars, ref, event, start, end),
        )
        if (!names.containsKey(ref)) {
            names[ref] = event.serviceName.ifBlank { ref }
        }
    }
    val out = ArrayList<MultiEpgChannel>(byService.size)
    for ((ref, bars) in byService) {
        bars.sortBy { it.startSec }
        val name = names[ref] ?: ref
        val prev = previousChannels[ref]
        val barsList: List<MultiEpgBar> =
            if (prev != null && sameBarInstances(prev.bars, bars)) {
                prev.bars
            } else {
                bars
            }
        out.add(
            if (prev != null && prev.serviceName == name && prev.bars === barsList) {
                prev
            } else {
                MultiEpgChannel(
                    serviceRef = ref,
                    serviceName = name,
                    bars = barsList,
                )
            },
        )
    }
    if (out.size == previous.size && out.indices.all { out[it] === previous[it] }) {
        return previous
    }
    return out
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
