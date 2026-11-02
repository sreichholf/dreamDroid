package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Media3VideoTest {
    @Test
    fun aPictureReportsItsSizeAndSquarePixels() {
        assertEquals(
            VideoLayout(1920, 1080, 1920, 1080, 1000, 1000),
            Media3Video.layout(width = 1920, height = 1080, pixelWidthHeightRatio = 1f)
        )
    }

    @Test
    fun anAnamorphicPictureKeepsItsSampleAspectRatio() {
        val layout = requireNotNull(
            Media3Video.layout(width = 720, height = 576, pixelWidthHeightRatio = 64f / 45f)
        )

        assertEquals(720, layout.width)
        assertEquals(576, layout.height)
        assertEquals(1422, layout.sarNum)
        assertEquals(Media3Video.SAR_SCALE, layout.sarDen)
    }

    @Test
    fun aStreamWithoutAPictureSizeHasNoLayout() {
        assertNull(Media3Video.layout(width = 0, height = 1080, pixelWidthHeightRatio = 1f))
        assertNull(Media3Video.layout(width = 1920, height = 0, pixelWidthHeightRatio = 1f))
    }
}
