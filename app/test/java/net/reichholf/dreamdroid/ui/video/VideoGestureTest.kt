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
