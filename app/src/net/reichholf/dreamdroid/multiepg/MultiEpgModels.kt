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
 */
fun buildMultiEpgChannels(events: List<Event>): List<MultiEpgChannel> {
    if (events.isEmpty()) return emptyList()
    val byService = LinkedHashMap<String, MutableList<MultiEpgBar>>()
    val names = HashMap<String, String>()
    for (event in events) {
        val ref = event.serviceReference.trim()
        if (ref.isEmpty()) continue
        val start = event.start.toLongOrNull() ?: continue
        val duration = event.duration.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        byService.getOrPut(ref) { ArrayList() }.add(
            MultiEpgBar(event = event, startSec = start, endSec = start + duration),
        )
        if (!names.containsKey(ref)) {
            names[ref] = event.serviceName.ifBlank { ref }
        }
    }
    return byService.map { (ref, bars) ->
        bars.sortBy { it.startSec }
        MultiEpgChannel(
            serviceRef = ref,
            serviceName = names[ref] ?: ref,
            bars = bars,
        )
    }
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
