package net.reichholf.dreamdroid.ui.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoGestureTest {
    @Test
    fun horizontalTravelFiresOnceThenResets() {
        val swipe = VideoChannelSwipe()
        assertEquals(VideoSwipe.None, swipe.onScroll(40f, 5f, 100f))
        assertEquals(VideoSwipe.Next, swipe.onScroll(70f, 5f, 100f))
        assertEquals(VideoSwipe.None, swipe.onScroll(80f, 0f, 100f))
        swipe.reset()
        assertEquals(VideoSwipe.Previous, swipe.onScroll(-120f, 10f, 100f))
    }

    @Test
    fun verticalScrollDoesNotZap() {
        val swipe = VideoChannelSwipe()
        assertEquals(VideoSwipe.None, swipe.onScroll(10f, 80f, 50f))
    }
}

class BrightnessSwipeTest {
    @Test
    fun withoutAnOverrideTheSwipeStartsFromTheSystemBrightness() {
        assertEquals(0.6f, brightnessAfterSwipe(-1f, 255, -0.4f), 1e-4f)
        assertEquals(0.5f + 0.1f, brightnessAfterSwipe(-1f, null, 0.1f), 1e-4f)
    }

    @Test
    fun anOverrideIsTheStartAndTheResultStaysInRange() {
        assertEquals(0.4f, brightnessAfterSwipe(0.3f, 255, 0.1f), 1e-4f)
        assertEquals(0.01f, brightnessAfterSwipe(0.1f, 255, -0.5f), 1e-6f)
        assertEquals(1f, brightnessAfterSwipe(0.9f, 0, 0.5f), 1e-6f)
    }
}

class SwipeTargetTest {
    private fun target(
        startX: Float,
        distanceX: Float,
        distanceY: Float,
        enabled: Boolean = true,
        widthPixels: Int = 700
    ): SwipeTarget = swipeTarget(startX, widthPixels, distanceX, distanceY, enabled)

    @Test
    fun aVerticalSwipeInTheRightBandIsVolume() {
        assertEquals(SwipeTarget.Volume, target(600f, 10f, 80f))
        assertEquals(SwipeTarget.Volume, target(401f, -10f, -80f))
        assertEquals(SwipeTarget.Ignored, target(400f, 10f, 80f))
    }

    @Test
    fun aVerticalSwipeInTheLeftBandIsBrightness() {
        assertEquals(SwipeTarget.Brightness, target(100f, 10f, -80f))
        assertEquals(SwipeTarget.Brightness, target(299f, 10f, 80f))
        assertEquals(SwipeTarget.Ignored, target(300f, 10f, 80f))
    }

    @Test
    fun aVerticalSwipeInTheMiddleIsIgnored() {
        assertEquals(SwipeTarget.Ignored, target(350f, 10f, 80f))
    }

    @Test
    fun theBandsAreCutWithIntegerDivision() {
        // 3 * 1000 / 7 truncates to 428 and 4 * 1000 / 7 to 571; with float bounds the two
        // cases just above each bound would land in the opposite band.
        assertEquals(SwipeTarget.Brightness, target(427.9f, 10f, 80f, widthPixels = 1000))
        assertEquals(SwipeTarget.Ignored, target(428.3f, 10f, 80f, widthPixels = 1000))
        assertEquals(SwipeTarget.Ignored, target(571f, 10f, 80f, widthPixels = 1000))
        assertEquals(SwipeTarget.Volume, target(571.2f, 10f, 80f, widthPixels = 1000))
    }

    @Test
    fun aHorizontalSwipeIsAChannelSwipeWhereverItStarts() {
        assertEquals(SwipeTarget.Channel, target(100f, 120f, 0f))
        assertEquals(SwipeTarget.Channel, target(350f, -120f, 0f))
        assertEquals(SwipeTarget.Channel, target(600f, 120f, 40f))
    }

    @Test
    fun aDisabledGestureGoesNowhere() {
        assertEquals(SwipeTarget.Ignored, target(600f, 10f, 80f, enabled = false))
        assertEquals(SwipeTarget.Ignored, target(100f, 10f, 80f, enabled = false))
        assertEquals(SwipeTarget.Ignored, target(350f, 120f, 0f, enabled = false))
    }

    @Test
    fun noTravelOrEqualTravelIsIgnored() {
        assertEquals(SwipeTarget.Ignored, target(600f, 0f, 0f))
        assertEquals(SwipeTarget.Ignored, target(600f, 30f, 30f))
        assertEquals(SwipeTarget.Ignored, target(350f, -30f, 30f))
    }
}

class VolumeSwipeTest {
    @Test
    fun theDeltaIsTheDistanceOverTheSurfaceHeightAsPercent() {
        assertEquals(10f, volumeDeltaPercent(80f, 800), 1e-4f)
        assertEquals(-5f, volumeDeltaPercent(-40f, 800), 1e-4f)
        assertEquals(100f, volumeDeltaPercent(500f, 500), 1e-4f)
    }

    @Test
    fun aStoredVolumeWinsOverTheSystemValue() {
        assertEquals(50f, volumePercentAfterSwipe(0f, 40f, 10f), 1e-4f)
        assertEquals(50f, volumePercentAfterSwipe(-1f, 40f, 10f), 1e-4f)
        assertEquals(40f, volumePercentAfterSwipe(30f, 100f, 10f), 1e-4f)
    }

    @Test
    fun theResultStaysBetweenZeroAndHundred() {
        assertEquals(100f, volumePercentAfterSwipe(95f, 0f, 10f), 1e-4f)
        assertEquals(0f, volumePercentAfterSwipe(3f, 0f, -10f), 1e-4f)
    }

    @Test
    fun theStreamStepIsThePercentOfTheStreamRange() {
        assertEquals(7, streamVolumeFor(50f, 15))
        assertEquals(15, streamVolumeFor(100f, 15))
        assertEquals(0, streamVolumeFor(0f, 15))
        assertEquals(0, streamVolumeFor(50f, 0))
    }

    @Test
    fun oneDragRunsFromTheSystemVolumeToTheStreamStep() {
        val delta = volumeDeltaPercent(-160f, 800)
        val percent = volumePercentAfterSwipe(0f, 50f, delta)
        assertEquals(30f, percent, 1e-4f)
        assertEquals(4, streamVolumeFor(percent, 15))
    }
}
