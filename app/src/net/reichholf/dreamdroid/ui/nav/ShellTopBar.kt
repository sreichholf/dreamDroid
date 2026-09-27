package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import net.reichholf.dreamdroid.R

/**
 * One top-bar action. With [iconRes] it is an icon button whose content description is
 * [label]; without one it is a text button showing [label].
 */
data class ShellTopBarAction(
    val id: Int,
    val label: String,
    @DrawableRes val iconRes: Int? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Title and destination actions for the phone shell's [TopAppBar]. The title follows
 * the activity title. Every attached [BindShellTopBarActions] keeps a binding; the
 * newest one is shown. When it leaves (a pop, or a cancelled predictive-back preview
 * of the previous destination), the newest remaining binding shows again.
 */
class ShellTopBarController {
    var title by mutableStateOf("")

    var actions by mutableStateOf<List<ShellTopBarAction>>(emptyList())
        private set

    private val bindings = sortedMapOf<Int, List<ShellTopBarAction>>()
    private var nextEpoch = 0

    internal fun claim(): Int {
        nextEpoch += 1
        return nextEpoch
    }

    internal fun bind(epoch: Int, actions: List<ShellTopBarAction>) {
        bindings[epoch] = actions
        publish()
    }

    internal fun update(epoch: Int, actions: List<ShellTopBarAction>) {
        if (epoch in bindings) {
            bindings[epoch] = actions
            publish()
        }
    }

    internal fun release(epoch: Int) {
        if (bindings.remove(epoch) != null) {
            publish()
        }
    }

    private fun publish() {
        val newest = if (bindings.isEmpty()) emptyList() else bindings.getValue(bindings.lastKey())
        if (newest !== actions) {
            actions = newest
        }
    }
}

val LocalShellTopBarController = staticCompositionLocalOf<ShellTopBarController?> { null }

/** Shows [actions] in the shell top bar while this composition is attached. */
@Composable
fun BindShellTopBarActions(actions: List<ShellTopBarAction>) {
    val controller = LocalShellTopBarController.current ?: return
    val epoch = remember(controller) { controller.claim() }
    DisposableEffect(controller, epoch) {
        controller.bind(epoch, actions)
        onDispose { controller.release(epoch) }
    }
    SideEffect { controller.update(epoch, actions) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellTopAppBar(
    controller: ShellTopBarController,
    onNavigationClick: () -> Unit,
    trailingActions: List<ShellTopBarAction>,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(text = controller.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu_drawer),
                    contentDescription = stringResource(R.string.drawer_open)
                )
            }
        },
        actions = {
            (controller.actions + trailingActions).forEach { action ->
                ShellTopBarActionButton(action)
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
private fun ShellTopBarActionButton(action: ShellTopBarAction) {
    val iconRes = action.iconRes
    if (iconRes == null) {
        TextButton(onClick = action.onClick, enabled = action.enabled) {
            Text(action.label)
        }
    } else {
        IconButton(onClick = action.onClick, enabled = action.enabled) {
            Icon(painter = painterResource(iconRes), contentDescription = action.label)
        }
    }
}
