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
