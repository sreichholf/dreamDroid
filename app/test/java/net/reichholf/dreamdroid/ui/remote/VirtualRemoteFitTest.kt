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
    fun shortPaneFillsHeightWithoutOverflow() {
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
        assertTrue(metrics.fitsWithoutScroll)
        assertEquals(500f, height, 1f)
        assertTrue(metrics.keyWidth < VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP)
    }

    @Test
    fun tallPaneGrowsUntilWidthIsSpent() {
        val availableWidth = 328f
        val metrics = VirtualRemoteFit.metrics(
            availableWidthDp = availableWidth,
            availableHeightDp = 800f,
            layout = VirtualRemoteLayout.Full
        )
        val padWidth = 5f * metrics.keyWidth + 4f * metrics.gap
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
        assertTrue(metrics.fitsWithoutScroll)
        assertEquals(availableWidth, padWidth, 1f)
        assertTrue(
            "full pad must grow past the 56dp XML key when height allows",
            metrics.keyWidth > VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP
        )
        assertTrue("grown pad must stay below the tall pane ($height)", height <= 800.5f)
    }

    @Test
    fun quickZapGrowsToTheSameWidthAsFullWhenHeightAllows() {
        val availableWidth = 328f
        val full = VirtualRemoteFit.metrics(
            availableWidthDp = availableWidth,
            availableHeightDp = 800f,
            layout = VirtualRemoteLayout.Full
        )
        val quick = VirtualRemoteFit.metrics(
            availableWidthDp = availableWidth,
            availableHeightDp = 800f,
            layout = VirtualRemoteLayout.QuickZap
        )
        assertEquals(full.keyWidth, quick.keyWidth, 0.05f)
        val quickHeight = VirtualRemoteFit.padHeightDp(
            layout = VirtualRemoteLayout.QuickZap,
            keyWidth = quick.keyWidth,
            keyHeight = quick.keyHeight,
            keyHeightLow = quick.keyHeightLow,
            navKeySize = quick.navKeySize,
            gap = quick.gap,
            verticalPadding = quick.verticalPadding,
            sectionExtra = quick.sectionExtra
        )
        assertTrue(quick.fitsWithoutScroll)
        assertTrue(quickHeight < 800f)
        assertTrue(quick.navKeySize > quick.keyWidth)
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
