package net.reichholf.dreamdroid.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** A window wide enough for two list-detail panes (expanded width). */
val EXPANDED_WINDOW_WIDTH = 900.dp

/** A phone-sized window (compact width). */
val COMPACT_WINDOW_WIDTH = 400.dp

/**
 * Reports a [width] x [height] window to [content] through [LocalWindowInfo], which
 * `currentWindowAdaptiveInfoV2()` reads for the window size class. The activity's own
 * configuration and layout bounds stay as they are: on the CI phone emulator two "expanded"
 * panes share its real width (list-detail panes shrink in proportion), so tests assert where
 * things are relative to each other, not how wide they are.
 */
@Composable
fun WithWindowSize(width: Dp, height: Dp = 900.dp, content: @Composable () -> Unit) {
    val size = with(LocalDensity.current) { IntSize(width.roundToPx(), height.roundToPx()) }
    CompositionLocalProvider(LocalWindowInfo provides FixedWindowInfo(size), content = content)
}

private class FixedWindowInfo(override val containerSize: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
}
