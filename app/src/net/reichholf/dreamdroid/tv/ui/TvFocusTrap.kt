package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps D-pad focus in a modal overlay.
 *
 * A move that would leave the group is cancelled before focus changes, so the screen behind the
 * overlay never takes focus or scrolls. [focusProperties] has to come before [focusGroup]: the
 * properties apply to the focus target below them, the group.
 */
internal fun Modifier.tvFocusTrap(): Modifier = focusProperties {
    onExit = { cancelFocusChange() }
}.focusGroup()

/**
 * Requests [focus] once [overlayShown] turns false. A modal overlay cancels focus that would leave
 * it while it is composed, so the opener can only take focus back after the overlay is gone.
 */
@Composable
internal fun RestoreFocusAfterOverlay(overlayShown: Boolean, focus: FocusRequester) {
    var wasShown by remember { mutableStateOf(false) }
    LaunchedEffect(overlayShown) {
        val restore = wasShown && !overlayShown
        wasShown = overlayShown
        if (restore) {
            withFrameNanos {}
            focus.tryRequestFocus()
        }
    }
}

/** A focus request that may find its target out of the composition. */
internal fun FocusRequester.tryRequestFocus(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    false
}
