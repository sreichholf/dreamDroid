package net.reichholf.dreamdroid.ui.nav

/**
 * Bottom margin that keeps the shell FAB above destination chrome, with the M3 16dp gap.
 * A static rest margin leaves the timer FAB under the now-playing strip.
 */
internal fun dodgedBottomMarginPx(
    chromeVisible: Boolean,
    chromeHeightPx: Int,
    gapPx: Int,
    restMarginPx: Int
): Int = if (chromeVisible && chromeHeightPx > 0) {
    gapPx + chromeHeightPx
} else {
    restMarginPx
}
