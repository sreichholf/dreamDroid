package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Keeps D-pad focus inside a modal overlay.
 *
 * `focusGroup()` alone is not a trap: a direction key with no target inside the group moves
 * focus to the chrome behind it. This consumes such a key and moves focus itself, putting it
 * back on the previously focused child when the move left the group. Keys the focused child
 * consumes never reach this handler.
 *
 * [fallback] is the overlay's first action, used when the previously focused child is gone.
 */
@Composable
internal fun Modifier.tvFocusTrap(fallback: FocusRequester): Modifier {
    val focusManager = LocalFocusManager.current
    val trapFocus = remember { FocusRequester() }
    return this
        .focusRequester(trapFocus)
        .focusGroup()
        .onKeyEvent { event ->
            val direction = event.focusDirectionOrNull() ?: return@onKeyEvent false
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            trapFocus.saveFocusedChild()
            focusManager.moveFocus(direction)
            if (!trapFocus.saveFocusedChild() && !trapFocus.restoreFocusedChild()) {
                if (!fallback.requestFocus()) {
                    // Last resort: enter the group at its first focusable child.
                    trapFocus.requestFocus()
                }
            }
            true
        }
}

private fun KeyEvent.focusDirectionOrNull(): FocusDirection? = when (key) {
    Key.DirectionUp -> FocusDirection.Up
    Key.DirectionDown -> FocusDirection.Down
    Key.DirectionLeft -> FocusDirection.Left
    Key.DirectionRight -> FocusDirection.Right
    else -> null
}
