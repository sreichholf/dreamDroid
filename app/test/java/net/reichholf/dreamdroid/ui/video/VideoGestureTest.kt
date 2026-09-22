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
