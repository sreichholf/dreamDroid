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

    /**
     * Start of the last bar in [sortedBars] that begins before [currentStartSec].
     * [sortedBars] must already be ordered by [MultiEpgBar.startSec].
     */
    fun prevStartSec(sortedBars: List<MultiEpgBar>, currentStartSec: Long): Long? {
        var lo = 0
        var hi = sortedBars.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedBars[mid].startSec < currentStartSec) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return sortedBars.getOrNull(lo - 1)?.startSec
    }

    /**
     * Start of a bar's text inside the bar, so the title of a programme that began
     * left of the viewport sticks to [viewportStartDp] instead of scrolling away.
     */
    fun stickyTextInsetDp(barStartDp: Float, barWidthDp: Float, viewportStartDp: Float): Float =
        (viewportStartDp - barStartDp).coerceIn(0f, barWidthDp.coerceAtLeast(0f))

    /**
     * Width left for a bar's text after the sticky inset, or 0 when that is below
     * [minTextWidthDp]: a sliver shows no text instead of "T…".
     */
    fun textWidthDp(
        barStartDp: Float,
        barWidthDp: Float,
        viewportStartDp: Float,
        minTextWidthDp: Float
    ): Float {
        val width = barWidthDp - stickyTextInsetDp(barStartDp, barWidthDp, viewportStartDp)
        return if (width < minTextWidthDp) 0f else width
    }

    /**
     * Spans of at least [minGapSec] inside [fromSec]..[toSec] that no bar in [sortedBars]
     * covers: the holes before the first programme, between programmes, and after the last.
     * [sortedBars] must already be ordered by [MultiEpgBar.startSec].
     */
    fun gaps(
        sortedBars: List<MultiEpgBar>,
        fromSec: Long,
        toSec: Long,
        minGapSec: Long
    ): List<LongRange> {
        val result = ArrayList<LongRange>()
        var cursor = fromSec
        for (bar in sortedBars) {
            if (bar.endSec <= cursor) {
                continue
            }
            if (bar.startSec >= toSec) {
                break
            }
            if (bar.startSec - cursor >= minGapSec) {
                result.add(cursor until bar.startSec)
            }
            cursor = maxOf(cursor, bar.endSec)
        }
        if (toSec - cursor >= minGapSec) {
            result.add(cursor until toSec)
        }
        return result
    }
}
