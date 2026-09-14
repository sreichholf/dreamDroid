package net.reichholf.dreamdroid.ui.remote

/**
 * Size the on-screen RCU so a full pad fits the visible pane. Preferred key sizes match the
 * historical XML dimens (`remote_key_width` 56dp / `remote_key_height` 40dp /
 * `remote_key_height_low` 30dp). Width-only sizing grew keys until the pad ran off the
 * phone's lower edge; height is part of the budget now.
 */
internal object VirtualRemoteFit {
    const val GAP_DP = 4f
    const val VERTICAL_PADDING_DP = 24f
    const val SECTION_SPACING_EXTRA_DP = 2f
    const val PREFERRED_KEY_WIDTH_DP = 56f
    const val PREFERRED_KEY_HEIGHT_DP = 40f
    const val PREFERRED_KEY_HEIGHT_LOW_DP = 30f
    const val MIN_KEY_WIDTH_DP = 36f
    const val MAX_QUICK_ZAP_NAV_DP = 72f

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

    fun metrics(
        availableWidthDp: Float,
        availableHeightDp: Float,
        layout: VirtualRemoteLayout
    ): Metrics {
        val widthKey = ((availableWidthDp - GAP_DP * 4f) / 5f)
            .coerceAtMost(PREFERRED_KEY_WIDTH_DP)
            .coerceAtLeast(MIN_KEY_WIDTH_DP)
        val widthScale = widthKey / PREFERRED_KEY_WIDTH_DP
        var keyWidth = widthKey
        var keyHeight = PREFERRED_KEY_HEIGHT_DP * widthScale
        var keyHeightLow = PREFERRED_KEY_HEIGHT_LOW_DP * widthScale
        var gap = GAP_DP
        var verticalPadding = VERTICAL_PADDING_DP
        var sectionExtra = SECTION_SPACING_EXTRA_DP
        var navKeySize = navKeySize(layout, keyWidth)
        val height = padHeightDp(
            layout = layout,
            keyWidth = keyWidth,
            keyHeight = keyHeight,
            keyHeightLow = keyHeightLow,
            navKeySize = navKeySize,
            gap = gap,
            verticalPadding = verticalPadding,
            sectionExtra = sectionExtra
        )
        if (height > availableHeightDp + 0.5f && height > 0f && availableHeightDp > 0f) {
            val scale = availableHeightDp / height
            keyWidth *= scale
            keyHeight *= scale
            keyHeightLow *= scale
            gap *= scale
            verticalPadding *= scale
            sectionExtra *= scale
            navKeySize = navKeySize(layout, keyWidth)
        }
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
            (keyWidth * 1.15f).coerceAtMost(MAX_QUICK_ZAP_NAV_DP)
        } else {
            keyWidth
        }
}
