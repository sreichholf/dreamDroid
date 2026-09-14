package net.reichholf.dreamdroid.ui.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualRemoteFitTest {
    @Test
    fun preferredFullPadIsAboutOriginalXmlHeight() {
        val height = VirtualRemoteFit.padHeightDp(
            layout = VirtualRemoteLayout.Full,
            keyWidth = 56f,
            keyHeight = 40f,
            keyHeightLow = 30f
        )
        // Number + color + nav + mute + transport + source + section gaps + padding.
        assertEquals(562f, height, 0.5f)
    }

    @Test
    fun wideShortPhoneShrinksKeysSoFullPadFits() {
        val metrics = VirtualRemoteFit.metrics(
            availableWidthDp = 328f,
            availableHeightDp = 500f,
            layout = VirtualRemoteLayout.Full
        )
        val height = VirtualRemoteFit.padHeightDp(
            layout = VirtualRemoteLayout.Full,
            keyWidth = metrics.keyWidth,
            keyHeight = metrics.keyHeight,
            keyHeightLow = metrics.keyHeightLow,
            navKeySize = metrics.navKeySize,
            gap = metrics.gap,
            verticalPadding = metrics.verticalPadding,
            sectionExtra = metrics.sectionExtra
        )
        assertTrue(
            "pad height $height must fit in 500dp (keys ${metrics.keyWidth}x${metrics.keyHeight})",
            height <= 500.5f
        )
        assertTrue(metrics.fitsWithoutScroll)
        assertTrue(metrics.keyWidth >= VirtualRemoteFit.MIN_KEY_WIDTH_DP)
        assertTrue(
            "keys must shrink below the 56dp XML preferred width",
            metrics.keyWidth < VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP
        )
    }

    @Test
    fun tallPaneKeepsPreferredKeyWidth() {
        val metrics = VirtualRemoteFit.metrics(
            availableWidthDp = 328f,
            availableHeightDp = 800f,
            layout = VirtualRemoteLayout.Full
        )
        assertEquals(VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP, metrics.keyWidth, 0.01f)
        assertEquals(VirtualRemoteFit.PREFERRED_KEY_HEIGHT_DP, metrics.keyHeight, 0.01f)
        assertTrue(metrics.fitsWithoutScroll)
    }

    @Test
    fun extremelyShortPaneFallsBackToScrollRatherThanTinyKeys() {
        val metrics = VirtualRemoteFit.metrics(
            availableWidthDp = 328f,
            availableHeightDp = 180f,
            layout = VirtualRemoteLayout.Full
        )
        assertEquals(VirtualRemoteFit.MIN_KEY_WIDTH_DP, metrics.keyWidth, 0.01f)
        assertFalse(metrics.fitsWithoutScroll)
    }
}
