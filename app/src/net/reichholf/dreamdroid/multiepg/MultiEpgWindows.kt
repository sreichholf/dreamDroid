package net.reichholf.dreamdroid.multiepg

import kotlin.math.max

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
     * [originFloorSec] is "now" (left clamp). Chunks entirely at or before that
     * instant are omitted. Padding keeps one chunk behind and ahead of the
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
}
