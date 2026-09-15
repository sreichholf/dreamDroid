package net.reichholf.dreamdroid.ui.nav

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity

/**
 * Bind the Coordinator [R.id.fab_main]. In-content Compose FABs clip: AppBarLayout
 * ScrollingViewBehavior makes [R.id.detail_view] taller than the visible area (see
 * `dualpane.xml`), and zeroed Scaffold insets (#263) leave the control under the
 * gesture bar. List destinations already use this slot.
 */
@Composable
fun BindShellFab(contentDescription: String, @DrawableRes iconRes: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(activity, contentDescription, iconRes) {
        val fab = activity?.findViewById<FloatingActionButton>(R.id.fab_main)
        fab?.let { button ->
            button.show()
            button.contentDescription = contentDescription
            button.setImageResource(iconRes)
            button.setOnClickListener { latestOnClick() }
            button.setOnLongClickListener { view ->
                Toast.makeText(activity, view.contentDescription, Toast.LENGTH_SHORT).show()
                true
            }
        }
        onDispose {
            fab?.let { button ->
                button.setOnClickListener(null)
                button.setOnLongClickListener(null)
                button.hide()
            }
            (activity as? MainActivity)?.unregisterFab(R.id.fab_main)
        }
    }
}
