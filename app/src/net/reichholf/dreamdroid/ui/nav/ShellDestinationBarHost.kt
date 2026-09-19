package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.services.TvMoviesDestinationRail
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.services.TvMoviesShellChrome
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.tools.ToolsDestinationBar
import net.reichholf.dreamdroid.ui.tools.ToolsDestinationRail
import net.reichholf.dreamdroid.ui.tools.ToolsHubState

/**
 * Which hub (if any) currently owns the activity Coordinator destination bar.
 *
 * Held as Snapshot state so the *shell* ComposeView composition can recompose when hubs
 * publish / clear themselves — without capturing NavHost `@Composable` lambdas into that
 * sibling ComposeView (the #355 regression).
 */
sealed interface ShellDestinationBarContent {
    data object Hidden : ShellDestinationBarContent
    data class Tools(val state: ToolsHubState) : ShellDestinationBarContent
    data class TvMovies(val state: TvMoviesHubState) : ShellDestinationBarContent
}

/**
 * Activity-chrome controller for [R.id.shell_destination_nav] (and tablet
 * [R.id.shell_destination_rail] when that slot exists).
 * Owned by [ProvideShellDestinationBar] for the lifetime of [PhoneNavHost], not by hub leaves.
 */
class ShellDestinationBarController {
    var content by mutableStateOf<ShellDestinationBarContent>(ShellDestinationBarContent.Hidden)
}

val LocalShellDestinationBarController = staticCompositionLocalOf<ShellDestinationBarController> {
    error(
        "ShellDestinationBarController not provided — wrap PhoneNavHost in " +
            "ProvideShellDestinationBar"
    )
}

/**
 * True when the activity hosts [R.id.shell_destination_rail] (sw720dp). Hubs skip the
 * bottom destination-bar spacer in that case — destinations live on the start rail.
 */
val LocalShellUsesDestinationRail = staticCompositionLocalOf { false }

const val SHELL_HUB_DESTINATION_BAR_SPACER_TAG = "shell_hub_destination_bar_spacer"
const val SHELL_HUB_NOW_PLAYING_SPACER_TAG = "shell_hub_now_playing_spacer"

/**
 * Installs long-lived compositions on the activity shell chrome slots for the
 * lifetime of this host (the phone/tablet NavHost), then provides
 * [LocalShellDestinationBarController].
 *
 * Phone ([R.id.shell_destination_rail] absent): [ToolsDestinationBar] /
 * [TvMoviesShellChrome] on [R.id.shell_destination_nav].
 * Tablet: destinations on [R.id.shell_destination_rail]; the bottom slot is
 * now-playing only (Tools hides it).
 *
 * Hub destinations only publish [ShellDestinationBarContent] via [RegisterShellDestinationBar].
 * That keeps shell chrome out of hub content recomposition / load cycles — the failure mode when
 * [InstallShellDestinationBar] lived inside Tools / TV & Movies and vanished after screenshot or
 * bouquet load finished.
 */
@Composable
fun ProvideShellDestinationBar(content: @Composable () -> Unit) {
    val controller = remember { ShellDestinationBarController() }
    val view = LocalView.current
    val usesRail = remember(view) {
        view.context.findActivity()?.findViewById<View?>(R.id.shell_destination_rail) != null
    }
    DisposableEffect(view) {
        val activity = view.context.findActivity()
            ?: return@DisposableEffect onDispose { }
        val shellNav = activity.findViewById<ComposeView?>(R.id.shell_destination_nav)
            ?: return@DisposableEffect onDispose { }
        val shellRail = activity.findViewById<ComposeView?>(R.id.shell_destination_rail)
        val strategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        shellNav.setViewCompositionStrategy(strategy)
        if (shellRail == null) {
            shellNav.setContent {
                DreamDroidTheme {
                    PhoneShellNavContent(controller, shellNav)
                }
            }
        } else {
            shellRail.setViewCompositionStrategy(strategy)
            shellRail.setContent {
                DreamDroidTheme {
                    TabletShellRailContent(controller, shellRail)
                }
            }
            shellNav.setContent {
                DreamDroidTheme {
                    TabletShellNavContent(controller, shellNav)
                }
            }
        }
        onDispose {
            shellNav.visibility = View.GONE
            shellNav.disposeComposition()
            shellRail?.visibility = View.GONE
            shellRail?.disposeComposition()
        }
    }
    CompositionLocalProvider(
        LocalShellDestinationBarController provides controller,
        LocalShellUsesDestinationRail provides usesRail
    ) {
        content()
    }
}

/**
 * Bottom space matching visible Coordinator chrome: now-playing strip when on, and the
 * destination bar only on phone (tablet destinations are on the start rail).
 */
@Composable
fun ShellHubBottomChromeSpacer(
    nowPlayingStripEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val showBar = !LocalShellUsesDestinationRail.current
    if (!nowPlayingStripEnabled && !showBar) {
        return
    }
    Column(modifier) {
        if (nowPlayingStripEnabled) {
            Spacer(
                Modifier
                    .height(dimensionResource(R.dimen.now_playing_strip_height))
                    .testTag(SHELL_HUB_NOW_PLAYING_SPACER_TAG)
            )
        }
        if (showBar) {
            Spacer(
                Modifier
                    .height(dimensionResource(R.dimen.shell_destination_bar_height))
                    .testTag(SHELL_HUB_DESTINATION_BAR_SPACER_TAG)
            )
        }
    }
}

@Composable
private fun PhoneShellNavContent(controller: ShellDestinationBarController, shellNav: ComposeView) {
    val shown = controller.content
    SideEffect {
        shellNav.visibility =
            if (shown is ShellDestinationBarContent.Hidden) View.GONE else View.VISIBLE
        if (shown !is ShellDestinationBarContent.Hidden) {
            shellNav.bringToFront()
        }
    }
    when (shown) {
        ShellDestinationBarContent.Hidden -> Unit

        is ShellDestinationBarContent.Tools -> ToolsDestinationBar(
            selected = shown.state.selected,
            onDestinationSelected = { shown.state.onDestinationSelected(it) }
        )

        is ShellDestinationBarContent.TvMovies -> TvMoviesShellChrome(
            state = shown.state
        )
    }
}

@Composable
private fun TabletShellRailContent(
    controller: ShellDestinationBarController,
    shellRail: ComposeView
) {
    val shown = controller.content
    SideEffect {
        shellRail.visibility =
            if (shown is ShellDestinationBarContent.Hidden) View.GONE else View.VISIBLE
    }
    when (shown) {
        ShellDestinationBarContent.Hidden -> Unit

        is ShellDestinationBarContent.Tools -> ToolsDestinationRail(
            selected = shown.state.selected,
            onDestinationSelected = { shown.state.onDestinationSelected(it) }
        )

        is ShellDestinationBarContent.TvMovies -> TvMoviesDestinationRail(
            selected = shown.state.selected,
            onDestinationSelected = { shown.state.onDestinationSelected(it) }
        )
    }
}

@Composable
private fun TabletShellNavContent(
    controller: ShellDestinationBarController,
    shellNav: ComposeView
) {
    val shown = controller.content
    val showStrip = shown is ShellDestinationBarContent.TvMovies &&
        shown.state.nowPlayingStripEnabled
    SideEffect {
        shellNav.visibility = if (showStrip) View.VISIBLE else View.GONE
        if (showStrip) {
            shellNav.bringToFront()
        }
    }
    when (shown) {
        is ShellDestinationBarContent.TvMovies -> TvMoviesShellChrome(
            state = shown.state,
            showDestinationBar = false
        )

        else -> Unit
    }
}

/**
 * Publishes [content] to the shell Coordinator bar while this leaf is composed.
 * Clears only if we still own the slot (so rapid hub→hub swaps do not blank a successor).
 */
@Composable
fun RegisterShellDestinationBar(content: ShellDestinationBarContent) {
    val controller = LocalShellDestinationBarController.current
    DisposableEffect(controller, content) {
        controller.content = content
        onDispose {
            if (controller.content == content) {
                controller.content = ShellDestinationBarContent.Hidden
            }
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
