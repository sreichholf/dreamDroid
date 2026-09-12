package net.reichholf.dreamdroid.multiepg

/**
 * Align MultiEPG cache chunks to fixed unix windows (default 24 h).
 */
object MultiEpgWindows {
    const val CHUNK_SECONDS: Long = 24L * 60L * 60L
    const val DEFAULT_TTL_MS: Long = 25L * 60L * 1000L

    data class Chunk(val startSec: Long, val endSec: Long)

    fun chunkContaining(unixSec: Long, chunkSeconds: Long = CHUNK_SECONDS): Chunk {
        require(chunkSeconds > 0L) { "chunkSeconds must be positive" }
        val start = unixSec.floorDiv(chunkSeconds) * chunkSeconds
        return Chunk(start, start + chunkSeconds)
    }
}
