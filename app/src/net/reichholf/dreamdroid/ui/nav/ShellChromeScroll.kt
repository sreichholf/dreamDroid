package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.BottomAppBarState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

/**
 * Hide-on-scroll state of the shell chrome. Scrolling content down slides the top bar up
 * and the bottom chrome (destination bar and now-playing strip) down; scrolling up brings
 * them back. Both follow Material 3 app bar state: [topBar] through the enter-always
 * behavior, [bottomBar] through the exit-always behavior and [collapsingBottomChrome].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class ShellChromeScrollState {
    val topBar = TopAppBarState(
        initialHeightOffsetLimit = -Float.MAX_VALUE,
        initialHeightOffset = 0f,
        initialContentOffset = 0f
    )

    val bottomBar = BottomAppBarState(
        initialHeightOffsetLimit = -Float.MAX_VALUE,
        initialHeightOffset = 0f,
        initialContentOffset = 0f
    )

    /** Bottom chrome comes back; the top bar waits for a scroll up. */
    fun revealBottom() {
        bottomBar.heightOffset = 0f
        bottomBar.contentOffset = 0f
    }

    fun revealAll() {
        topBar.heightOffset = 0f
        topBar.contentOffset = 0f
        revealBottom()
    }

    /**
     * Keeps where the bars are in the back stack entry of the screen that is leaving, as
     * Material 3 keeps each screen's saveable app bar state.
     */
    fun saveTo(handle: SavedStateHandle) {
        handle[KEY_TOP_OFFSET] = topBar.heightOffset
        handle[KEY_TOP_CONTENT_OFFSET] = topBar.contentOffset
        handle[KEY_BOTTOM_OFFSET] = bottomBar.heightOffset
        handle[KEY_BOTTOM_CONTENT_OFFSET] = bottomBar.contentOffset
    }

    /**
     * Puts the bars back where the screen of [handle] left them, so Back returns to the bars
     * as they were. A screen shown for the first time starts with all bars shown.
     */
    fun restoreFrom(handle: SavedStateHandle) {
        val topOffset = handle.get<Float>(KEY_TOP_OFFSET)
        if (topOffset == null) {
            revealAll()
            return
        }
        topBar.heightOffset = topOffset
        topBar.contentOffset = handle.get<Float>(KEY_TOP_CONTENT_OFFSET) ?: 0f
        bottomBar.heightOffset = handle.get<Float>(KEY_BOTTOM_OFFSET) ?: 0f
        bottomBar.contentOffset = handle.get<Float>(KEY_BOTTOM_CONTENT_OFFSET) ?: 0f
    }
}

private const val KEY_TOP_OFFSET = "shell_chrome_top_offset"
private const val KEY_TOP_CONTENT_OFFSET = "shell_chrome_top_content_offset"
private const val KEY_BOTTOM_OFFSET = "shell_chrome_bottom_offset"
private const val KEY_BOTTOM_CONTENT_OFFSET = "shell_chrome_bottom_content_offset"

val LocalShellChromeScrollState = staticCompositionLocalOf<ShellChromeScrollState?> { null }

/**
 * Gives each screen its own bar positions, as Material 3 keeps app bar state per screen: a
 * screen shown for the first time starts with all shell chrome shown, and Back finds the bars
 * where that screen left them. [entry] is the back stack entry of the screen on display.
 */
@Composable
fun KeepShellChromePerScreen(entry: NavBackStackEntry?) {
    val chromeScroll = LocalShellChromeScrollState.current
    DisposableEffect(entry, chromeScroll) {
        if (entry == null || chromeScroll == null) {
            return@DisposableEffect onDispose {}
        }
        chromeScroll.restoreFrom(entry.savedStateHandle)
        // Pausing comes before the instance state is saved, so rotation and process death
        // keep the bars too.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                chromeScroll.saveTo(entry.savedStateHandle)
            }
        }
        entry.lifecycle.addObserver(observer)
        onDispose {
            entry.lifecycle.removeObserver(observer)
            // A popped entry is gone; only a screen that may come back keeps its bars.
            if (entry.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                chromeScroll.saveTo(entry.savedStateHandle)
            }
        }
    }
}

/**
 * Lays out the bottom chrome at its visible height only, as
 * [androidx.compose.material3.BottomAppBar] does for its fixed height: the content above grows as the chrome slides out, and the part that has left the
 * screen is clipped. The chrome's measured height is the state's offset limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.collapsingBottomChrome(state: ShellChromeScrollState): Modifier = clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val bar = state.bottomBar
        val limit = -placeable.height.toFloat()
        if (bar.heightOffsetLimit != limit) {
            // Hidden chrome stays hidden when it grows, as when the now-playing strip turns on.
            val hidden = bar.heightOffset < 0f && bar.heightOffset <= bar.heightOffsetLimit
            bar.heightOffsetLimit = limit
            if (hidden) {
                bar.heightOffset = limit
            }
        }
        val visible = (placeable.height + bar.heightOffset.roundToInt())
            .coerceIn(0, placeable.height)
        layout(placeable.width, visible) {
            placeable.place(0, 0)
        }
    }

/**
 * True while TalkBack or another touch-exploration service runs. The shell then keeps its
 * chrome in place, since a hidden bar is hard to reach without scrolling gestures.
 */
@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        enabled = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
