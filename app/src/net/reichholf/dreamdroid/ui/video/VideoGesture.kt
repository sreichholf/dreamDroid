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
