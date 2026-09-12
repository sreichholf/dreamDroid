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

/** Bars that intersect [windowStartSec, windowEndSec). */
fun List<MultiEpgBar>.overlapping(windowStartSec: Long, windowEndSec: Long): List<MultiEpgBar> {
    if (windowEndSec <= windowStartSec) return emptyList()
    return filter { it.startSec < windowEndSec && it.endSec > windowStartSec }
}
