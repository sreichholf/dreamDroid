package net.reichholf.dreamdroid.ui.nav

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Bottom inset for phone NavHost leaves that do not show the shell destination bar.
 *
 * `detail_view` uses AppBarLayout.ScrollingViewBehavior and MainActivity is
 * edge-to-edge. Destinations zero Scaffold contentWindowInsets (#263) so the XML
 * app bar is not double-padded; that also drops the bottom system-bar inset.
 *
 * [WindowInsets.safeDrawing] is often already consumed by the DrawerLayout, so
 * pad by the measured overlap of this ComposeView with the visible safe area.
 *
 * Hubs keep the overflow on purpose (the shell draws their bottom chrome).
 */
@Composable
fun Modifier.phoneNavDestinationViewport(shellBarVisible: Boolean): Modifier {
    val overflow = rememberPhoneNavBottomOverflowDp()
    // The shell already pads by the keyboard; counting it here again squeezed every
    // destination to a sliver while the keyboard was open.
    val safeBottom = WindowInsets.safeDrawing
        .exclude(WindowInsets.ime)
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    return phoneNavDestinationViewport(
        shellBarVisible = shellBarVisible,
        bottomInset = max(overflow, safeBottom)
    )
}

fun Modifier.phoneNavDestinationViewport(shellBarVisible: Boolean, bottomInset: Dp): Modifier =
    if (shellBarVisible || bottomInset <= 0.dp) {
        this
    } else {
        padding(bottom = bottomInset)
    }

/**
 * Hide the shell destination chrome as soon as the current route is not a hub.
 * Hub [RegisterShellDestinationBar] only clears on dispose, which runs after the
 * first frame of timer/profile edit.
 * A [floating] (dialog) destination floats over the hub, which stays composed, so the bar stays.
 */
fun applyShellDestinationBarForRoute(
    route: String?,
    floating: Boolean,
    controller: ShellDestinationBarController?
) {
    if (floating || PhoneNavRoutes.showsShellDestinationBar(route)) {
        return
    }
    controller?.content = ShellDestinationBarContent.Hidden
}

internal fun phoneNavBottomOverflowPx(
    viewTopInWindow: Int,
    viewHeight: Int,
    windowHeight: Int,
    safeBottomInset: Int
): Int {
    val viewBottom = viewTopInWindow + viewHeight
    val visibleBottom = windowHeight - safeBottomInset
    return (viewBottom - visibleBottom).coerceAtLeast(0)
}

@Composable
private fun rememberPhoneNavBottomOverflowDp(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var overflowPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        fun measure() {
            overflowPx = view.phoneNavBottomOverflowPx()
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { measure() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        measure()
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return with(density) { overflowPx.toDp() }
}

private fun View.phoneNavBottomOverflowPx(): Int {
    if (!isAttachedToWindow || height <= 0) {
        return 0
    }
    val insets = ViewCompat.getRootWindowInsets(this)
    val safeBottom = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
    val loc = IntArray(2)
    getLocationInWindow(loc)
    return phoneNavBottomOverflowPx(
        viewTopInWindow = loc[1],
        viewHeight = height,
        windowHeight = rootView.height,
        safeBottomInset = safeBottom
    )
}
