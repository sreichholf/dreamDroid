package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import kotlin.math.max
import kotlin.math.min

/**
 * Align MultiEPG cache chunks to fixed unix windows (default 24 h).
 */
object MultiEpgWindows {
    const val CHUNK_SECONDS: Long = 24L * 60L * 60L
    const val DEFAULT_TTL_MS: Long = 25L * 60L * 1000L
    /** Hard cap so a wild visible range cannot request an unbounded dump. */
    const val MAX_SLIDING_CHUNKS: Int = 8

    data class Chunk(val startSec: Long, val endSec: Long)

    fun chunkContaining(unixSec: Long, chunkSeconds: Long = CHUNK_SECONDS): Chunk {
        require(chunkSeconds > 0L) { "chunkSeconds must be positive" }
        val start = unixSec.floorDiv(chunkSeconds) * chunkSeconds
        return Chunk(start, start + chunkSeconds)
    }

    /**
     * UTC cache chunk starts that cover a painted sliding window.
     *
     * [originFloorSec] is "now" (chunk load clamp). Chunks entirely at or before
     * that instant are omitted. Padding keeps one chunk behind and ahead of the
     * viewport so pan does not hit a wall; a chunk leaves the set only after
     * it no longer overlaps that padded range (off-screen front or back).
     */
    fun slidingChunks(
        originFloorSec: Long,
        visibleStartSec: Long,
        visibleEndSec: Long,
        chunkSeconds: Long = CHUNK_SECONDS,
        padSec: Long = CHUNK_SECONDS,
    ): List<Long> {
        val visStart = max(originFloorSec, visibleStartSec)
        val visEnd = max(visStart + 60L, visibleEndSec)
        val from = max(originFloorSec, visStart - padSec)
        val to = visEnd + padSec
        val out = ArrayList<Long>(4)
        var start = chunkContaining(from, chunkSeconds).startSec
        while (start < to && out.size < MAX_SLIDING_CHUNKS) {
            val chunk = chunkContaining(start, chunkSeconds)
            if (chunk.endSec > originFloorSec) {
                out.add(chunk.startSec)
            }
            start = chunk.endSec
        }
        return out
    }

    /**
     * Left edge of the painted grid: earliest start among programmes that
     * overlap [nowSec] while the oldest loaded cache window still contains now.
     * If nothing is airing, the edge is now. After the now-window has been
     * dropped from the sliding set, the edge is the oldest remaining window.
     */
    fun paintedTimelineStart(
        nowSec: Long,
        minWindowStartSec: Long,
        events: List<Event>,
    ): Long {
        if (minWindowStartSec > nowSec) {
            return minWindowStartSec
        }
        var earliest: Long? = null
        for (event in events) {
            val start = event.start.toLongOrNull() ?: continue
            val duration = event.duration.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            if (start <= nowSec && start + duration > nowSec) {
                earliest = if (earliest == null) start else min(earliest, start)
            }
        }
        return earliest ?: nowSec
    }

    /**
     * Seconds to add to horizontal scroll so the wall-clock under the left edge
     * stays put when [paintedTimelineStart] moves.
     */
    fun originScrollCompensationSec(
        previousOriginSec: Long,
        newOriginSec: Long,
    ): Long {
        if (previousOriginSec == Long.MIN_VALUE ||
            previousOriginSec == 0L ||
            newOriginSec == 0L
        ) {
            return 0L
        }
        return previousOriginSec - newOriginSec
    }
}
