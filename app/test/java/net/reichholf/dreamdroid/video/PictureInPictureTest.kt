package net.reichholf.dreamdroid.video

import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PictureInPictureTest {
    @Test
    fun aNormalPictureKeepsItsAspectRatio() {
        assertEquals(16f / 9f, pictureInPictureAspectRatio(1920, 1080)!!, 0.0001f)
        assertEquals(4f / 3f, pictureInPictureAspectRatio(768, 576)!!, 0.0001f)
    }

    @Test
    fun anExtremePictureIsClampedIntoTheRangeTheSystemAccepts() {
        assertEquals(PIP_MAX_ASPECT_RATIO, pictureInPictureAspectRatio(3000, 1000)!!, 0f)
        assertEquals(PIP_MIN_ASPECT_RATIO, pictureInPictureAspectRatio(1000, 3000)!!, 0f)
    }

    @Test
    fun noReportedSizeHasNoAspectRatio() {
        assertNull(pictureInPictureAspectRatio(0, 0))
        assertNull(pictureInPictureAspectRatio(1920, 0))
        assertNull(pictureInPictureAspectRatio(0, 1080))
        assertNull(pictureInPictureAspectRatio(-1920, 1080))
    }

    /** setPictureInPictureParams rejects ratios outside 0.418410..2.39 with an exception. */
    @Test
    fun theBoundsSurviveTheRationalRoundTrip() {
        listOf(PIP_MIN_ASPECT_RATIO, PIP_MAX_ASPECT_RATIO).forEach { ratio ->
            val numerator = (ratio * PIP_ASPECT_RATIO_SCALE).roundToInt()
            val quantized = numerator / PIP_ASPECT_RATIO_SCALE.toFloat()
            assertTrue(quantized > 0.418410f, "min bound became $quantized")
            assertTrue(quantized <= 2.39f, "max bound became $quantized")
        }
    }

    @Test
    fun aWidePictureIsLetterboxedInATallContainer() {
        val hint = pictureInPictureSourceRectHint(PipRect(0, 0, 1080, 2400), 1920, 1080)
        assertEquals(PipRect(0, 896, 1080, 1504), hint)
    }

    @Test
    fun aNarrowPictureIsPillarboxedInAWideContainer() {
        val hint = pictureInPictureSourceRectHint(PipRect(0, 0, 2400, 1080), 768, 576)
        assertEquals(PipRect(480, 0, 1920, 1080), hint)
    }

    @Test
    fun theHintKeepsTheContainerOffset() {
        val hint = pictureInPictureSourceRectHint(PipRect(100, 50, 1100, 1050), 1920, 1080)
        assertEquals(PipRect(100, 268, 1100, 831), hint)
    }

    @Test
    fun aMatchingPictureFillsTheContainer() {
        val container = PipRect(0, 0, 1920, 1080)
        assertEquals(container, pictureInPictureSourceRectHint(container, 1280, 720))
    }

    @Test
    fun noReportedSizeOrAnEmptyContainerHasNoHint() {
        assertNull(pictureInPictureSourceRectHint(PipRect(0, 0, 1080, 2400), 0, 1080))
        assertNull(pictureInPictureSourceRectHint(PipRect(0, 0, 1080, 2400), 1920, 0))
        assertNull(pictureInPictureSourceRectHint(PipRect(0, 0, 0, 0), 1920, 1080))
    }
}
