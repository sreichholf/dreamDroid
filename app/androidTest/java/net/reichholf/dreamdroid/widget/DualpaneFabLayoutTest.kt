package net.reichholf.dreamdroid.widget

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualpaneFabLayoutTest {
    @Test
    fun mainFabUsesGravityNotDetailAnchorAndReloadFabRemoved() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = ContextThemeWrapper(base, R.style.Theme_DreamDroid_Night)
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dualpane, null, false)
        val fabMain = root.findViewById<FloatingActionButton>(R.id.fab_main)
        val mainLp = fabMain.layoutParams as CoordinatorLayout.LayoutParams

        assertEquals(View.NO_ID, mainLp.anchorId)
        assertTrue((mainLp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
        assertTrue((mainLp.gravity and Gravity.END) == Gravity.END)
        // Reload FAB removed; id must not remain in the package resources.
        val reloadId = context.resources.getIdentifier("fab_reload", "id", context.packageName)
        assertEquals(0, reloadId)
    }

    @Test
    fun shellDestinationBarUsesBottomGravityOnCoordinator() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = ContextThemeWrapper(base, R.style.Theme_DreamDroid_Night)
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dualpane, null, false)
        val nav = root.findViewById<ComposeView>(R.id.shell_destination_nav)
        val lp = nav.layoutParams as CoordinatorLayout.LayoutParams

        assertEquals(View.NO_ID, lp.anchorId)
        assertTrue((lp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
        assertEquals(View.GONE, nav.visibility)
    }
}
