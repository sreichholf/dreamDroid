package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

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
 * Title and destination actions for the phone shell's [TopAppBar]. Destinations report
 * the title from their UI state with [ShellTitle]. Every attached [BindShellTopBarActions]
 * or [ReplaceShellTopBar] keeps a binding; the newest one is shown. When it leaves (a pop,
 * or a cancelled predictive-back preview of the previous destination), the newest
 * remaining binding shows again.
 */
class ShellTopBarController {
    var title by mutableStateOf("")

    var actions by mutableStateOf<List<ShellTopBarAction>>(emptyList())
        private set

    /** True while the newest binding is a [ReplaceShellTopBar]: the shell hides its bar. */
    var replaced by mutableStateOf(false)
        private set

    /** True while the newest binding keeps the bar in view when content scrolls. */
    var keepInView by mutableStateOf(false)
        private set

    /** [actions] null: the destination draws its own bar instead of the shell's. */
    private class Binding(val actions: List<ShellTopBarAction>?, val keepInView: Boolean)

    private val bindings = sortedMapOf<Int, Binding>()
    private var nextEpoch = 0

    internal fun claim(): Int {
        nextEpoch += 1
        return nextEpoch
    }

    internal fun bind(epoch: Int, actions: List<ShellTopBarAction>?, keepInView: Boolean = false) {
        bindings[epoch] = Binding(actions, keepInView)
        publish()
    }

    internal fun update(epoch: Int, actions: List<ShellTopBarAction>) {
        val binding = bindings[epoch] ?: return
        bindings[epoch] = Binding(actions, binding.keepInView)
        publish()
    }

    internal fun release(epoch: Int) {
        if (epoch in bindings) {
            bindings.remove(epoch)
            publish()
        }
    }

    private fun publish() {
        val newest = if (bindings.isEmpty()) null else bindings.getValue(bindings.lastKey())
        replaced = newest != null && newest.actions == null
        keepInView = newest?.keepInView == true
        val shown = newest?.actions ?: emptyList()
        if (shown !== actions) {
            actions = shown
        }
    }
}

val LocalShellTopBarController = staticCompositionLocalOf<ShellTopBarController?> { null }

/** Reports a destination's title from its UI state to the shell top bar. */
@Composable
fun ShellTitle(title: UiText) {
    val controller = LocalShellTopBarController.current ?: return
    val text = title.asString()
    LaunchedEffect(controller, text) {
        controller.title = text
    }
}

/**
 * Shows [actions] in the shell top bar while this composition is attached. With
 * [keepInView] the bar stays put while content scrolls, for screens whose Save lives there.
 */
@Composable
fun BindShellTopBarActions(actions: List<ShellTopBarAction>, keepInView: Boolean = false) {
    val controller = LocalShellTopBarController.current ?: return
    val epoch = remember(controller) { controller.claim() }
    DisposableEffect(controller, epoch, keepInView) {
        controller.bind(epoch, actions, keepInView)
        onDispose { controller.release(epoch) }
    }
    SideEffect { controller.update(epoch, actions) }
}

/**
 * Hides the shell top bar while this composition is attached, for a destination that draws
 * its own bar in its content, such as EPG search with its search field.
 */
@Composable
fun ReplaceShellTopBar() {
    val controller = LocalShellTopBarController.current ?: return
    val epoch = remember(controller) { controller.claim() }
    DisposableEffect(controller, epoch) {
        controller.bind(epoch, null)
        onDispose { controller.release(epoch) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellTopAppBar(
    controller: ShellTopBarController,
    onNavigationClick: () -> Unit,
    trailingActions: List<ShellTopBarAction>,
    scrollBehavior: TopAppBarScrollBehavior,
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
        windowInsets = WindowInsets(0, 0, 0, 0),
        scrollBehavior = scrollBehavior
    )
}

/** [action] as a top app bar button: its icon, or its label without one. */
@Composable
fun ShellTopBarActionButton(action: ShellTopBarAction) {
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
