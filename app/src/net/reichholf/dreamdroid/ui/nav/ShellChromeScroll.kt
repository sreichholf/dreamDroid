package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Hide-on-scroll state of the shell chrome. Scrolling content down slides the top bar up
 * and the bottom chrome (destination bar and now-playing strip) down, and collapses the FAB
 * to its icon; scrolling up brings them back.
 *
 * The top bar follows [topBar] through the Material 3 enter-always behavior. The bottom
 * chrome follows [bottomHiddenFraction] through [collapsingBottomChrome].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class ShellChromeScrollState {
    val topBar = TopAppBarState(
        initialHeightOffsetLimit = -Float.MAX_VALUE,
        initialHeightOffset = 0f,
        initialContentOffset = 0f
    )

    /**
     * How much of the bottom chrome has slid out, from 0 (shown) to 1 (hidden). A fraction,
     * so hidden chrome stays hidden when the now-playing strip turns on.
     */
    var bottomHiddenFraction by mutableFloatStateOf(0f)
        private set

    /** False while content scrolls down: the shell FAB shows its icon only. */
    var fabExpanded by mutableStateOf(true)
        private set

    /** Measured height of the bottom chrome; written during layout, read when scrolling. */
    internal var bottomHeightPx = 0f

    /** Bumped by every move except a settle, so a running settle stops writing. */
    private var moves = 0

    /** Bottom chrome and FAB label come back; the top bar waits for a scroll up. */
    fun revealBottom() {
        moves += 1
        bottomHiddenFraction = 0f
        fabExpanded = true
    }

    fun revealAll() {
        topBar.heightOffset = 0f
        topBar.contentOffset = 0f
        revealBottom()
    }

    /**
     * Moves the bottom chrome and FAB label with a vertical scroll of [deltaY] pixels
     * (negative while content scrolls down). Consumes nothing: the content keeps scrolling.
     */
    internal fun onScroll(deltaY: Float) {
        if (deltaY == 0f) {
            return
        }
        moves += 1
        if (bottomHeightPx > 0f) {
            bottomHiddenFraction =
                (bottomHiddenFraction - deltaY / bottomHeightPx).coerceIn(0f, 1f)
        }
        fabExpanded = deltaY > 0f
    }

    /**
     * After a fling, a half-hidden bottom chrome snaps to the nearer end. A scroll or reveal
     * during the snap wins: the snap stops writing.
     */
    internal suspend fun settleBottom() {
        val start = bottomHiddenFraction
        if (start <= 0f || start >= 1f) {
            return
        }
        val settle = moves
        animate(start, if (start < 0.5f) 0f else 1f) { value, _ ->
            if (moves == settle) {
                bottomHiddenFraction = value
            }
        }
    }

    internal fun bottomConnection(enabled: () -> Boolean): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (enabled()) {
                    onScroll(available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (enabled()) {
                    settleBottom()
                }
                return Velocity.Zero
            }
        }
}

val LocalShellChromeScrollState = staticCompositionLocalOf<ShellChromeScrollState?> { null }

/**
 * Lays out the bottom chrome at its visible height only, so the content above grows as it
 * slides out, and clips the part that has left the screen.
 */
fun Modifier.collapsingBottomChrome(state: ShellChromeScrollState): Modifier = clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        state.bottomHeightPx = placeable.height.toFloat()
        val visible = (placeable.height * (1f - state.bottomHiddenFraction)).roundToInt()
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
