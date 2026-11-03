package net.reichholf.dreamdroid.ui.video

import kotlin.math.abs

enum class VideoSwipe {
    None,
    Previous,
    Next
}

/**
 * Horizontal overlay swipe. Fires once per gesture after [thresholdPx] of travel.
 * Positive [distanceX] (finger moving left) is [VideoSwipe.Next].
 */
class VideoChannelSwipe {
    private var accumulatedX: Float = 0f
    private var fired: Boolean = false

    fun onScroll(distanceX: Float, distanceY: Float, thresholdPx: Float): VideoSwipe {
        if (abs(distanceY) >= abs(distanceX) || distanceX == 0f) {
            return VideoSwipe.None
        }
        if (fired) {
            return VideoSwipe.None
        }
        accumulatedX += distanceX
        if (abs(accumulatedX) < thresholdPx) {
            return VideoSwipe.None
        }
        fired = true
        return if (accumulatedX > 0f) VideoSwipe.Next else VideoSwipe.Previous
    }

    fun reset() {
        accumulatedX = 0f
        fired = false
    }
}

/** Where a scroll on the player goes: the volume or brightness band, the channels, or nowhere. */
enum class SwipeTarget {
    Volume,
    Brightness,
    Channel,
    Ignored
}

/**
 * Routes a scroll on the player from where it started. Disabled gestures go nowhere. A mostly
 * vertical drag counts only in the outer bands: right of 4/7 of the width it is
 * [SwipeTarget.Volume], left of 3/7 it is [SwipeTarget.Brightness], and the middle keeps the
 * drag for the overlays. A mostly horizontal drag is [SwipeTarget.Channel]. Equal travel, and a
 * drag with no travel at all, is [SwipeTarget.Ignored].
 *
 * [startX] is the raw x of the down event and [widthPixels] the width the bands are cut from.
 */
fun swipeTarget(
    startX: Float,
    widthPixels: Int,
    distanceX: Float,
    distanceY: Float,
    gesturesEnabled: Boolean
): SwipeTarget {
    if (!gesturesEnabled) return SwipeTarget.Ignored
    return when {
        abs(distanceY) > abs(distanceX) && distanceY != 0f ->
            when {
                startX > 4 * widthPixels / 7 -> SwipeTarget.Volume
                startX < 3 * widthPixels / 7 -> SwipeTarget.Brightness
                else -> SwipeTarget.Ignored
            }

        abs(distanceX) > abs(distanceY) && distanceX != 0f -> SwipeTarget.Channel

        else -> SwipeTarget.Ignored
    }
}

/** Volume travel of a vertical drag, in percent of the full range. */
fun volumeDeltaPercent(distanceY: Float, surfaceHeight: Int): Float =
    distanceY / surfaceHeight * 100

/**
 * The volume after a swipe. The player keeps [stored] between the events of one drag so it moves
 * smoothly, and starts from [systemPercent] instead while nothing is stored (0 or less). The
 * result is clamped to 0..100.
 */
fun volumePercentAfterSwipe(stored: Float, systemPercent: Float, delta: Float): Float {
    val start = if (stored > 0f) stored else systemPercent
    return (start + delta).coerceIn(0f, 100f)
}

/** The stream step for [percent] of [maxVolume]. A stream without steps always gets 0. */
fun streamVolumeFor(percent: Float, maxVolume: Int): Int = (percent / 100 * maxVolume).toInt()

/**
 * A negative [windowOverride] is BRIGHTNESS_OVERRIDE_NONE: the swipe then starts from the system
 * brightness (0..255), or from the middle when that cannot be read.
 */
fun brightnessAfterSwipe(windowOverride: Float, systemBrightness: Int?, delta: Float): Float {
    val start =
        when {
            windowOverride >= 0f -> windowOverride
            systemBrightness != null -> systemBrightness.coerceIn(0, 255) / 255f
            else -> 0.5f
        }
    return (start + delta).coerceIn(0.01f, 1f)
}
