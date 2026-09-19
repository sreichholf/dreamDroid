package net.reichholf.dreamdroid.widget

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DodgeShellChromeBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualpaneFabLayoutTest {
    @Test
    fun mainFabUsesGravityNotDetailAnchorAndReloadFabRemoved() {
        val fabMain = inflateMainFab()
        val mainLp = fabMain.layoutParams as CoordinatorLayout.LayoutParams

        assertEquals(View.NO_ID, mainLp.anchorId)
        assertTrue((mainLp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
        assertTrue((mainLp.gravity and Gravity.END) == Gravity.END)
        assertTrue(mainLp.behavior is DodgeShellChromeBehavior)
        // Reload FAB removed; id must not remain in the package resources.
        val reloadId = fabMain.context.resources.getIdentifier(
            "fab_reload",
            "id",
            fabMain.context.packageName
        )
        assertEquals(0, reloadId)
    }

    @Test
    fun mainFabIsExtendedAndTogglesLabel() {
        val fabMain = inflateMainFab()
        fabMain.text = "Add Profile"
        fabMain.extend()
        assertEquals("Add Profile", fabMain.text.toString())
        assertTrue(fabMain.isExtended)

        fabMain.text = ""
        fabMain.shrink()
        assertEquals("", fabMain.text.toString())
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

    @Test
    fun tabletDualpaneHostsGoneRailKeepingBottomNavAndFab() {
        val root = inflateTabletDualpane()
        val rail = root.findViewById<ComposeView>(R.id.shell_destination_rail)
        assertNotNull(rail)
        val railLp = rail.layoutParams

        assertEquals(View.GONE, rail.visibility)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, railLp.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, railLp.height)
        assertEquals(R.id.tablet_shell_row, (rail.parent as View).id)

        val nav = root.findViewById<ComposeView>(R.id.shell_destination_nav)
        val navLp = nav.layoutParams as CoordinatorLayout.LayoutParams
        assertTrue(nav.parent is CoordinatorLayout)
        assertTrue((navLp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)

        val fab = root.findViewById<ExtendedFloatingActionButton>(R.id.fab_main)
        val fabLp = fab.layoutParams as CoordinatorLayout.LayoutParams
        assertEquals(View.NO_ID, fabLp.anchorId)
        assertTrue((fabLp.gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
        assertTrue((fabLp.gravity and Gravity.END) == Gravity.END)
        assertTrue(fabLp.behavior is DodgeShellChromeBehavior)
    }

    private fun inflateTabletDualpane(): View {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration)
        config.smallestScreenWidthDp = 720
        val ctx = ContextThemeWrapper(
            base.createConfigurationContext(config),
            R.style.Theme_DreamDroid_Night
        )
        return LayoutInflater.from(ctx).inflate(R.layout.dualpane, null, false)
    }

    private fun inflateMainFab(): ExtendedFloatingActionButton {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = ContextThemeWrapper(base, R.style.Theme_DreamDroid_Night)
        val root = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dualpane, null, false)
        return root.findViewById(R.id.fab_main)
    }
}
