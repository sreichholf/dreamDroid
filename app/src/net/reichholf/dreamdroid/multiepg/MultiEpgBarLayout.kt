package net.reichholf.dreamdroid.multiepg

/**
 * Programme-bar geometry in dp. Short events keep [MIN_WIDTH_DP] unless that
 * would paint over the next event's start; then the bar is clipped.
 */
object MultiEpgBarLayout {
    const val MIN_WIDTH_DP: Float = 28f

    fun offsetDp(startSec: Long, timelineStartSec: Long, minuteWidthDp: Float): Float {
        val drawStart = maxOf(startSec, timelineStartSec)
        return minuteWidthDp * ((drawStart - timelineStartSec) / 60f)
    }

    fun widthDp(
        startSec: Long,
        endSec: Long,
        timelineStartSec: Long,
        minuteWidthDp: Float,
        nextStartSec: Long? = null,
        minWidthDp: Float = MIN_WIDTH_DP
    ): Float {
        val drawStart = maxOf(startSec, timelineStartSec)
        if (endSec <= timelineStartSec) {
            return 0f
        }
        val durationMin = maxOf((endSec - drawStart) / 60f, 1f)
        val desired = maxOf(minuteWidthDp * durationMin, minWidthDp)
        val untilNext = nextStartSec ?: return desired
        val thisX = offsetDp(drawStart, timelineStartSec, minuteWidthDp)
        val nextX = offsetDp(untilNext, timelineStartSec, minuteWidthDp)
        val gap = nextX - thisX
        if (gap <= 0f) {
            return 0f
        }
        return minOf(desired, gap)
    }

    /**
     * Start of the first bar in [sortedBars] that begins after [currentStartSec].
     * [sortedBars] must already be ordered by [MultiEpgBar.startSec].
     */
    fun nextStartSec(sortedBars: List<MultiEpgBar>, currentStartSec: Long): Long? {
        var lo = 0
        var hi = sortedBars.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedBars[mid].startSec <= currentStartSec) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return sortedBars.getOrNull(lo)?.startSec
    }
}
