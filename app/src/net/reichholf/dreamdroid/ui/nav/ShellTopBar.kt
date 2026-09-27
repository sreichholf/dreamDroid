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
 * the activity title. Actions come from the destination that bound last; a leaving
 * destination that recomposes after its successor bound does not take them back.
 */
class ShellTopBarController {
    var title by mutableStateOf("")

    var actions by mutableStateOf<List<ShellTopBarAction>>(emptyList())
        private set

    private var owner = 0
    private var nextOwner = 0

    internal fun claim(): Int {
        nextOwner += 1
        return nextOwner
    }

    internal fun bind(epoch: Int, actions: List<ShellTopBarAction>) {
        owner = epoch
        this.actions = actions
    }

    internal fun update(epoch: Int, actions: List<ShellTopBarAction>) {
        if (owner == epoch) {
            this.actions = actions
        }
    }

    internal fun release(epoch: Int) {
        if (owner == epoch) {
            owner = 0
            actions = emptyList()
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
