package net.reichholf.dreamdroid.multiepg

import kotlin.math.abs

/**
 * GraphMultiEPG `prev_time_period` spans: 60–300 minutes, default 120.
 * Density is dp-per-minute so N hours fill the same ~360.dp pane as 2 h.
 */
object MultiEpgZoom {
    const val DEFAULT_MINUTES: Int = 120
    const val BASE_MINUTE_WIDTH_DP: Float = 3f
    val OPTIONS_MINUTES: IntArray = intArrayOf(60, 120, 240, 300)

    fun coerce(minutes: Int): Int {
        var best = DEFAULT_MINUTES
        var bestDelta = Int.MAX_VALUE
        for (option in OPTIONS_MINUTES) {
            val delta = abs(option - minutes)
            if (delta < bestDelta) {
                best = option
                bestDelta = delta
            }
        }
        return best
    }

    fun minuteWidthDp(visibleMinutes: Int): Float {
        return BASE_MINUTE_WIDTH_DP * DEFAULT_MINUTES / coerce(visibleMinutes).toFloat()
    }

    fun hours(visibleMinutes: Int): Int = coerce(visibleMinutes) / 60

    /** Ruler step: half-hour when zoomed to 1 h, otherwise hourly. */
    fun tickStepSec(visibleMinutes: Int): Long {
        return if (coerce(visibleMinutes) <= 60) 1800L else 3600L
    }
}
