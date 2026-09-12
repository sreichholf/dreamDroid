package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.services.TvMoviesDestinationBar
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.tools.ToolsDestinationBar
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
 * Activity-chrome controller for [R.id.shell_destination_nav].
 * Owned by [ProvideShellDestinationBar] for the lifetime of [PhoneNavHost], not by hub leaves.
 */
class ShellDestinationBarController {
	var content by mutableStateOf<ShellDestinationBarContent>(ShellDestinationBarContent.Hidden)
}

val LocalShellDestinationBarController = staticCompositionLocalOf<ShellDestinationBarController> {
	error("ShellDestinationBarController not provided — wrap PhoneNavHost in ProvideShellDestinationBar")
}

/**
 * Installs a long-lived composition on the activity [R.id.shell_destination_nav] ComposeView
 * for the lifetime of this host (the phone NavHost), then provides [LocalShellDestinationBarController].
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
	DisposableEffect(view) {
		val activity = view.context.findActivity()
			?: return@DisposableEffect onDispose { }
		val shellNav = activity.findViewById<ComposeView?>(R.id.shell_destination_nav)
			?: return@DisposableEffect onDispose { }
		shellNav.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		shellNav.setContent {
			DreamDroidTheme {
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
						onDestinationSelected = { shown.state.onDestinationSelected(it) },
					)
					is ShellDestinationBarContent.TvMovies -> TvMoviesDestinationBar(
						selected = shown.state.selected,
						onDestinationSelected = { shown.state.onDestinationSelected(it) },
					)
				}
			}
		}
		onDispose {
			shellNav.visibility = View.GONE
			shellNav.disposeComposition()
		}
	}
	CompositionLocalProvider(LocalShellDestinationBarController provides controller) {
		content()
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

private fun Context.findActivity(): Activity? {
	var ctx: Context? = this
	while (ctx is ContextWrapper) {
		if (ctx is Activity) return ctx
		ctx = ctx.baseContext
	}
	return ctx as? Activity
}
