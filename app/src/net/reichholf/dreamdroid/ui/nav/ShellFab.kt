package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One shell FAB. [PhoneShell] renders it above destination chrome so list
 * destinations do not host a clipped in-content button.
 *
 * Pass [text] to show a labeled extended FAB (create-on-list). Omit it to shrink
 * to icon-only.
 *
 * Generation-gated so a successor [BindShellFab] wins when a NavHost remount
 * disposes the previous leaf. Clearing only happens when this epoch still owns
 * the slot.
 */
data class ShellFabSpec(
    val epoch: Int,
    val contentDescription: String,
    @DrawableRes val iconRes: Int,
    val text: String?,
    val lookDisabled: Boolean,
    val onClick: () -> Unit
)

class ShellFabController {
    var spec by mutableStateOf<ShellFabSpec?>(null)
}

val LocalShellFabController = staticCompositionLocalOf<ShellFabController?> { null }

const val SHELL_FAB_TAG = "shell_fab"

@Composable
fun BindShellFab(
    contentDescription: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    text: String? = null,
    lookDisabled: Boolean = false
) {
    val controller = LocalShellFabController.current ?: return
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(controller, contentDescription, iconRes, text, lookDisabled) {
        val epoch = ShellFabBindEpoch.claim()
        controller.spec = ShellFabSpec(
            epoch = epoch,
            contentDescription = contentDescription,
            iconRes = iconRes,
            text = text,
            lookDisabled = lookDisabled,
            onClick = { latestOnClick() }
        )
        onDispose {
            if (controller.spec?.epoch == epoch) {
                controller.spec = null
            }
        }
    }
}

internal object ShellFabBindEpoch {
    var current: Int = 0
        private set

    fun claim(): Int {
        current += 1
        return current
    }
}
