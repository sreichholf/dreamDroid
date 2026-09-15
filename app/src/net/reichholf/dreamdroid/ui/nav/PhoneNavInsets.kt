package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bottom inset for phone NavHost leaves that do not show the shell destination bar.
 *
 * `detail_view` uses AppBarLayout.ScrollingViewBehavior and MainActivity is
 * edge-to-edge. Destinations zero Scaffold contentWindowInsets (#263) so the XML
 * app bar is not double-padded; that also drops the bottom system-bar inset, so
 * last fields scroll under the gesture bar.
 *
 * Hubs keep that overflow on purpose: they reserve `shell_destination_bar_height`
 * and paint chrome on `shell_destination_nav`. Apply this padding only when that
 * bar is hidden instead of repeating it in every leaf.
 */
@Composable
fun Modifier.phoneNavDestinationViewport(shellBarVisible: Boolean): Modifier =
    phoneNavDestinationViewport(
        shellBarVisible = shellBarVisible,
        bottomSafe = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    )

fun Modifier.phoneNavDestinationViewport(
    shellBarVisible: Boolean,
    bottomSafe: WindowInsets
): Modifier = if (shellBarVisible) {
    this
} else {
    windowInsetsPadding(bottomSafe)
}
