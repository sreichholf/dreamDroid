package net.reichholf.dreamdroid.ui.nav

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
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
 * to icon-only (temporary for form Save until those screens drop the FAB).
 */
@Composable
fun BindShellFab(
    contentDescription: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    text: String? = null
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(activity, contentDescription, iconRes, text) {
        val fab = activity?.findViewById<ExtendedFloatingActionButton>(R.id.fab_main)
        fab?.let { button ->
            button.contentDescription = contentDescription
            button.setIconResource(iconRes)
            applyShellFabLabel(button, text)
            button.setOnClickListener { latestOnClick() }
            button.setOnLongClickListener { view ->
                Toast.makeText(activity, view.contentDescription, Toast.LENGTH_SHORT).show()
                true
            }
            button.show()
        }
        onDispose {
            fab?.let { button ->
                applyShellFabLabel(button, text = null)
                button.setOnClickListener(null)
                button.setOnLongClickListener(null)
                button.hide()
            }
            (activity as? MainActivity)?.unregisterFab(R.id.fab_main)
        }
    }
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
