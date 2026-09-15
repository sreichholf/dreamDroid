package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity

/**
 * Bind the Coordinator [R.id.fab_main]. In-content Compose FABs clip: AppBarLayout
 * ScrollingViewBehavior makes [R.id.detail_view] taller than the visible area (see
 * `dualpane.xml`), and zeroed Scaffold insets (#263) leave the control under the
 * gesture bar. List destinations already use this slot.
 *
 * Material 1.14 [ExtendedFloatingActionButton] extends MaterialButton, not
 * [com.google.android.material.floatingactionbutton.FloatingActionButton].
 *
 * Pass [text] to show a labeled extended FAB (create-on-list). Omit it to shrink
 * to icon-only.
 *
 * Hide is deferred and generation-gated. NavHost / load remounts run a successor
 * [BindShellFab] before the previous [DisposableEffect] disposes; an immediate
 * [ExtendedFloatingActionButton.hide] would finish after [show] and leave the
 * button gone (same race as the old leaf-owned destination bar).
 */
@Composable
fun BindShellFab(
    contentDescription: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    text: String? = null
) {
    val view = LocalView.current
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(view, contentDescription, iconRes, text) {
        val activity = view.context.findActivity()
        val fab = activity?.findViewById<ExtendedFloatingActionButton>(R.id.fab_main)
        val epoch = if (fab != null) {
            presentShellFab(
                button = fab,
                contentDescription = contentDescription,
                iconRes = iconRes,
                text = text,
                clickListener = View.OnClickListener { latestOnClick() },
                longClickListener = View.OnLongClickListener { clicked ->
                    Toast.makeText(
                        activity,
                        clicked.contentDescription,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            )
        } else {
            ShellFabBindEpoch.claim()
        }
        onDispose {
            if (fab != null) {
                releaseShellFab(button = fab, epoch = epoch, activity = activity)
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

    fun isCurrent(epoch: Int): Boolean = epoch == current
}

internal fun presentShellFab(
    button: ExtendedFloatingActionButton,
    contentDescription: String,
    @DrawableRes iconRes: Int,
    text: String?,
    clickListener: View.OnClickListener,
    longClickListener: View.OnLongClickListener
): Int {
    val epoch = ShellFabBindEpoch.claim()
    button.contentDescription = contentDescription
    button.setIconResource(iconRes)
    applyShellFabLabel(button, text)
    button.setOnClickListener(clickListener)
    button.setOnLongClickListener(longClickListener)
    button.show()
    return epoch
}

internal fun releaseShellFab(
    button: ExtendedFloatingActionButton,
    epoch: Int,
    activity: Activity?,
    deferUntilIdle: Boolean = true
) {
    val release = Runnable {
        if (!ShellFabBindEpoch.isCurrent(epoch)) {
            return@Runnable
        }
        applyShellFabLabel(button, text = null)
        button.setOnClickListener(null)
        button.setOnLongClickListener(null)
        button.hide()
        (activity as? MainActivity)?.unregisterFab(R.id.fab_main)
    }
    if (deferUntilIdle && button.post(release)) {
        return
    }
    release.run()
}

internal fun applyShellFabLabel(button: ExtendedFloatingActionButton, text: String?) {
    if (text.isNullOrEmpty()) {
        button.text = ""
        button.shrink()
    } else {
        button.text = text
        button.extend()
    }
}
