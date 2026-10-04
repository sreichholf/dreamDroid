package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.reichholf.dreamdroid.ui.services.TvMoviesDestinationRail
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.services.TvMoviesShellChrome
import net.reichholf.dreamdroid.ui.tools.ToolsDestinationBar
import net.reichholf.dreamdroid.ui.tools.ToolsDestinationRail
import net.reichholf.dreamdroid.ui.tools.ToolsHubState

/**
 * Which hub (if any) currently owns the shell destination chrome.
 *
 * Held as Snapshot state so [PhoneShell] recomposes when hubs publish or clear themselves,
 * without capturing NavHost `@Composable` lambdas into the shell (the #355 regression).
 */
sealed interface ShellDestinationBarContent {
    data object Hidden : ShellDestinationBarContent
    data class Tools(val state: ToolsHubState) : ShellDestinationBarContent
    data class TvMovies(val state: TvMoviesHubState) : ShellDestinationBarContent
}

/**
 * Shell destination chrome controller: the phone bottom chrome and the tablet rail.
 * Owned by [ProvideShellDestinationBar] for the lifetime of [PhoneNavHost], not by hub leaves.
 */
class ShellDestinationBarController {
    var content by mutableStateOf<ShellDestinationBarContent>(ShellDestinationBarContent.Hidden)
}

val LocalShellDestinationBarController =
    staticCompositionLocalOf<ShellDestinationBarController?> { null }

const val SHELL_CHROME_TAG = "shell_destination_chrome"

/**
 * Provides [LocalShellDestinationBarController] for the lifetime of this host (the
 * phone/tablet NavHost), reusing the shell's controller when there is one.
 *
 * Hub destinations only publish [ShellDestinationBarContent] via [RegisterShellDestinationBar].
 * That keeps shell chrome out of hub content recomposition and load cycles; the bar vanished
 * after screenshot or bouquet load when hubs installed it themselves.
 */
@Composable
fun ProvideShellDestinationBar(content: @Composable () -> Unit) {
    val parent = LocalShellDestinationBarController.current
    val controller = parent ?: remember { ShellDestinationBarController() }
    CompositionLocalProvider(LocalShellDestinationBarController provides controller) {
        content()
    }
}

/**
 * Phone bottom chrome (destination bar, and the TV & Movies now-playing strip when on).
 * Empty while the controller is [ShellDestinationBarContent.Hidden].
 */
@Composable
fun PhoneShellDestinationChrome(
    controller: ShellDestinationBarController,
    modifier: Modifier = Modifier
) {
    val shown = controller.content
    if (shown is ShellDestinationBarContent.Hidden) {
        return
    }
    Box(modifier.testTag(SHELL_CHROME_TAG)) {
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
}

/** Tablet start rail. Empty while no hub owns the shell. */
@Composable
fun TabletShellDestinationRail(
    controller: ShellDestinationBarController,
    modifier: Modifier = Modifier
) {
    val shown = controller.content
    if (shown is ShellDestinationBarContent.Hidden) {
        return
    }
    when (shown) {
        ShellDestinationBarContent.Hidden -> Unit

        is ShellDestinationBarContent.Tools -> ToolsDestinationRail(
            selected = shown.state.selected,
            onDestinationSelected = { shown.state.onDestinationSelected(it) },
            modifier = modifier
        )

        is ShellDestinationBarContent.TvMovies -> TvMoviesDestinationRail(
            selected = shown.state.selected,
            onDestinationSelected = { shown.state.onDestinationSelected(it) },
            modifier = modifier
        )
    }
}

/** Tablet bottom slot: now-playing only. Destinations live on the rail. */
@Composable
fun TabletShellNowPlaying(
    controller: ShellDestinationBarController,
    modifier: Modifier = Modifier
) {
    val shown = controller.content
    if (shown !is ShellDestinationBarContent.TvMovies || !shown.state.nowPlayingStripEnabled) {
        return
    }
    Box(modifier.testTag(SHELL_CHROME_TAG)) {
        TvMoviesShellChrome(
            state = shown.state,
            showDestinationBar = false
        )
    }
}

/**
 * Publishes [content] to the shell destination chrome while this leaf is composed, and again
 * each time its back stack entry resumes: the shell may have hidden the bar in between while
 * the leaf stayed composed.
 * Clears only if we still own the slot (so rapid hub→hub swaps do not blank a successor).
 */
@Composable
fun RegisterShellDestinationBar(content: ShellDestinationBarContent) {
    val controller = LocalShellDestinationBarController.current
        ?: error(
            "ShellDestinationBarController not provided — wrap PhoneNavHost in " +
                "ProvideShellDestinationBar"
        )
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, content, lifecycleOwner) {
        controller.content = content
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.content = content
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (controller.content == content) {
                controller.content = ShellDestinationBarContent.Hidden
            }
        }
    }
}
