package net.reichholf.dreamdroid.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu

/**
 * Shows a [PopupMenu] at a point inside a [ViewGroup].
 * Compose list rows have no View anchors; anchoring to the fragment root pins the
 * menu at the top-left. A 1×1 temporary child at the tap location restores row positioning.
 */
object AnchorPopup {
    fun interface Configurer {
        fun configure(menu: PopupMenu)
    }

    fun showAt(root: ViewGroup, xInRoot: Int, yInRoot: Int, configurer: Configurer) {
        val anchor = View(root.context)
        val lp = FrameLayout.LayoutParams(1, 1)
        lp.leftMargin = maxOf(0, xInRoot)
        lp.topMargin = maxOf(0, yInRoot)
        root.addView(anchor, lp)
        anchor.post {
            if (anchor.parent == null) {
                return@post
            }
            val menu = PopupMenu(anchor.context, anchor)
            menu.setOnDismissListener {
                (anchor.parent as? ViewGroup)?.removeView(anchor)
            }
            configurer.configure(menu)
            menu.show()
        }
    }

    /**
     * Overlay parent that honors [FrameLayout.LayoutParams] margins. [androidx.compose.ui.platform.LocalView]
     * is AndroidComposeView, which ignores those margins and pins the menu at (0, 0).
     */
    fun overlayRoot(view: View): ViewGroup =
        (view.rootView as? ViewGroup) ?: (view as? ViewGroup) ?: error("no ViewGroup for popup")

    /** Convert window coordinates to offsets inside [root]. */
    fun showAtWindow(root: ViewGroup, windowX: Int, windowY: Int, configurer: Configurer) {
        val loc = IntArray(2)
        root.getLocationInWindow(loc)
        showAt(root, windowX - loc[0], windowY - loc[1], configurer)
    }
}
