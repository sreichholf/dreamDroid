package net.reichholf.dreamdroid.ui.remote

import kotlin.math.min

/**
 * Size the on-screen RCU to the visible pane: grow until width or height is spent
 * ("just so"), then stop so the pad does not overflow. The baseline is the
 * historical XML key (56×40 / 30dp low); both axes scale together.
 */
internal object VirtualRemoteFit {
    const val GAP_DP = 4f
    const val VERTICAL_PADDING_DP = 24f
    const val SECTION_SPACING_EXTRA_DP = 2f
    const val PREFERRED_KEY_WIDTH_DP = 56f
    const val PREFERRED_KEY_HEIGHT_DP = 40f
    const val PREFERRED_KEY_HEIGHT_LOW_DP = 30f
    const val MIN_KEY_WIDTH_DP = 36f
    const val QUICK_ZAP_NAV_SCALE = 1.15f

    data class Metrics(
        val keyWidth: Float,
        val keyHeight: Float,
        val keyHeightLow: Float,
        val gap: Float,
        val navKeySize: Float,
        val verticalPadding: Float,
        val sectionExtra: Float,
        val fitsWithoutScroll: Boolean
    )

    fun preferredPadWidthDp(): Float = 5f * PREFERRED_KEY_WIDTH_DP + 4f * GAP_DP

    fun metrics(
        availableWidthDp: Float,
        availableHeightDp: Float,
        layout: VirtualRemoteLayout
    ): Metrics {
        val preferredNav = navKeySize(layout, PREFERRED_KEY_WIDTH_DP)
        val preferredHeight = padHeightDp(
            layout = layout,
            keyWidth = PREFERRED_KEY_WIDTH_DP,
            keyHeight = PREFERRED_KEY_HEIGHT_DP,
            keyHeightLow = PREFERRED_KEY_HEIGHT_LOW_DP,
            navKeySize = preferredNav
        )
        val preferredWidth = preferredPadWidthDp()
        val widthScale = if (preferredWidth > 0f && availableWidthDp > 0f) {
            availableWidthDp / preferredWidth
        } else {
            1f
        }
        val heightScale = if (preferredHeight > 0f && availableHeightDp > 0f) {
            availableHeightDp / preferredHeight
        } else {
            1f
        }
        val scale = min(widthScale, heightScale)
        var keyWidth = PREFERRED_KEY_WIDTH_DP * scale
        var keyHeight = PREFERRED_KEY_HEIGHT_DP * scale
        var keyHeightLow = PREFERRED_KEY_HEIGHT_LOW_DP * scale
        var gap = GAP_DP * scale
        var verticalPadding = VERTICAL_PADDING_DP * scale
        var sectionExtra = SECTION_SPACING_EXTRA_DP * scale
        var navKeySize = navKeySize(layout, keyWidth)
        val fits: Boolean
        if (keyWidth < MIN_KEY_WIDTH_DP) {
            val minScale = MIN_KEY_WIDTH_DP / keyWidth
            keyWidth = MIN_KEY_WIDTH_DP
            keyHeight *= minScale
            keyHeightLow *= minScale
            navKeySize = navKeySize(layout, keyWidth)
            fits = false
        } else {
            fits = padHeightDp(
                layout = layout,
                keyWidth = keyWidth,
                keyHeight = keyHeight,
                keyHeightLow = keyHeightLow,
                navKeySize = navKeySize,
                gap = gap,
                verticalPadding = verticalPadding,
                sectionExtra = sectionExtra
            ) <= availableHeightDp + 0.5f
        }
        return Metrics(
            keyWidth = keyWidth,
            keyHeight = keyHeight,
            keyHeightLow = keyHeightLow,
            gap = gap,
            navKeySize = navKeySize,
            verticalPadding = verticalPadding,
            sectionExtra = sectionExtra,
            fitsWithoutScroll = fits
        )
    }

    fun padHeightDp(
        layout: VirtualRemoteLayout,
        keyWidth: Float,
        keyHeight: Float,
        keyHeightLow: Float,
        navKeySize: Float = keyWidth,
        gap: Float = GAP_DP,
        verticalPadding: Float = VERTICAL_PADDING_DP,
        sectionExtra: Float = SECTION_SPACING_EXTRA_DP
    ): Float {
        val numberPad = 4f * keyHeight + 3f * gap
        val colorRow = keyHeightLow
        val navPad = 3f * navKeySize + 2f * gap
        val muteRow = keyHeightLow
        val transport = keyHeight
        val source = keyHeight
        val sections: Int
        val body: Float
        when (layout) {
            VirtualRemoteLayout.Full -> {
                sections = 6
                body = numberPad + colorRow + navPad + muteRow + transport + source
            }

            VirtualRemoteLayout.Simple -> {
                sections = 5
                body = numberPad + navPad + muteRow + colorRow + source
            }

            VirtualRemoteLayout.QuickZap -> {
                sections = 3
                val topRow = 2f * keyHeight + gap
                body = topRow + navPad + colorRow
            }
        }
        val sectionGaps = (sections - 1) * (2f * gap + sectionExtra)
        return body + sectionGaps + verticalPadding
    }

    private fun navKeySize(layout: VirtualRemoteLayout, keyWidth: Float): Float =
        if (layout == VirtualRemoteLayout.QuickZap) {
            keyWidth * QUICK_ZAP_NAV_SCALE
        } else {
            keyWidth
        }
}
