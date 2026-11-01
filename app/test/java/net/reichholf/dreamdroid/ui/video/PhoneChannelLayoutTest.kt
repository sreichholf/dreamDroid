package net.reichholf.dreamdroid.ui.video

import androidx.window.core.layout.WindowSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneChannelLayoutTest {
    @Test
    fun phonePortraitKeepsTheListBelowTheVideo() {
        assertEquals(PhoneChannelLayout.Portrait, phoneChannelLayout(WindowSizeClass(411, 891)))
    }

    @Test
    fun compactHeightIsLandscapeOnAnyWidth() {
        assertEquals(PhoneChannelLayout.Landscape, phoneChannelLayout(WindowSizeClass(891, 411)))
        assertEquals(PhoneChannelLayout.Landscape, phoneChannelLayout(WindowSizeClass(1280, 420)))
    }

    @Test
    fun expandedWidthIsTablet() {
        assertEquals(PhoneChannelLayout.Tablet, phoneChannelLayout(WindowSizeClass(1280, 800)))
    }

    @Test
    fun aWidePictureMovesUpByHalfTheFreeHeight() {
        assertEquals(-795f, videoTopTranslation(1080, 2400, 4f / 3f), 0.01f)
        assertEquals(-896.25f, videoTopTranslation(1080, 2400, 16f / 9f), 0.01f)
    }

    @Test
    fun aPictureFittedByHeightStays() {
        assertEquals(0f, videoTopTranslation(1080, 600, 16f / 9f), 0.01f)
    }
}
