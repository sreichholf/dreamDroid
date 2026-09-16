package net.reichholf.dreamdroid.widget

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnchorPopupTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun showAtUsesFrameLayoutMargins() {
        activityRule.scenario.onActivity { activity ->
            val root = FrameLayout(activity)
            activity.setContentView(root)
            AnchorPopup.showAt(root, 64, 128) { menu ->
                menu.menu.add("Zap")
            }
            assertEquals(1, root.childCount)
            val lp = root.getChildAt(0).layoutParams as FrameLayout.LayoutParams
            assertEquals(64, lp.leftMargin)
            assertEquals(128, lp.topMargin)
            // Posted PopupMenu.show() needs an AppCompat theme; drop the anchor first.
            root.removeAllViews()
        }
    }

    @Test
    fun overlayRootIsTheDecorViewNotTheComposeHost() {
        activityRule.scenario.onActivity { activity ->
            val composeHost = FrameLayout(activity)
            activity.setContentView(composeHost)
            val overlay = AnchorPopup.overlayRoot(composeHost)
            assertTrue(overlay === activity.window.decorView)
            assertTrue(overlay !== composeHost)
        }
    }

    @Test
    fun showAtWindowSubtractsRootWindowLocation() {
        activityRule.scenario.onActivity { activity ->
            val root = FrameLayout(activity)
            activity.setContentView(root)
            val loc = IntArray(2)
            root.getLocationInWindow(loc)
            AnchorPopup.showAtWindow(root, loc[0] + 40, loc[1] + 90) { menu ->
                menu.menu.add("Zap")
            }
            val lp = root.getChildAt(0).layoutParams as FrameLayout.LayoutParams
            assertEquals(40, lp.leftMargin)
            assertEquals(90, lp.topMargin)
            root.removeAllViews()
        }
    }
}
