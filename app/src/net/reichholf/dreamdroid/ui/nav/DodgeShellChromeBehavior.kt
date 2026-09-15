package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import net.reichholf.dreamdroid.R

/**
 * Sit [R.id.fab_main] above [R.id.shell_destination_nav] with the M3 16dp FAB gap.
 *
 * The Now-playing strip is composed inside that overlay (elevation 8dp vs FAB 6dp),
 * so a static [R.dimen.fab_margin_bottom] (legacy bottom-nav dodge) leaves the
 * timer FAB under the strip.
 */
class DodgeShellChromeBehavior(context: Context, attrs: AttributeSet?) :
    CoordinatorLayout.Behavior<View>(context, attrs) {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean = dependency.id == R.id.shell_destination_nav

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean = applyDodge(child, dependency, requestLayout = true)

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        val chrome = parent.findViewById<View>(R.id.shell_destination_nav)
        if (chrome != null) {
            applyDodge(child, chrome, requestLayout = false)
        }
        parent.onLayoutChild(child, layoutDirection)
        return true
    }
}

internal fun applyDodge(child: View, chrome: View, requestLayout: Boolean): Boolean {
    val desired = dodgedBottomMarginPx(
        chromeVisible = chrome.visibility == View.VISIBLE,
        chromeHeightPx = chrome.height,
        gapPx = child.resources.getDimensionPixelSize(R.dimen.fab_margin_above_chrome),
        restMarginPx = child.resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
    )
    val lp = child.layoutParams as? CoordinatorLayout.LayoutParams ?: return false
    if (lp.bottomMargin == desired) {
        return false
    }
    lp.bottomMargin = desired
    if (requestLayout) {
        child.requestLayout()
    }
    return true
}

internal fun dodgedBottomMarginPx(
    chromeVisible: Boolean,
    chromeHeightPx: Int,
    gapPx: Int,
    restMarginPx: Int
): Int = if (chromeVisible && chromeHeightPx > 0) {
    gapPx + chromeHeightPx
} else {
    restMarginPx
}
