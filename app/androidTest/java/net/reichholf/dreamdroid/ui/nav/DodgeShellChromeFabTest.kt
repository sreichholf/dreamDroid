package net.reichholf.dreamdroid.ui.nav

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.services.TvMoviesShellChrome
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Timer create FAB must sit above TV & Movies chrome (Now strip + destination bar).
 * The strip is in [R.id.shell_destination_nav], which paints over [R.id.fab_main].
 */
class DodgeShellChromeFabTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun newTimerFabSitsAboveNowPlayingStrip() {
        val (fab, shell) = hostTimerFabOverChrome(stripEnabled = true)
        waitUntilDodged(fab, shell)
        assertFabAboveChrome(fab, shell)
    }

    @Test
    fun newTimerFabSitsAboveDestinationBarWhenStripOff() {
        val (fab, shell) = hostTimerFabOverChrome(stripEnabled = false)
        waitUntilDodged(fab, shell)
        assertFabAboveChrome(fab, shell)
    }

    @Test
    fun restBottomMarginWhenChromeHidden() {
        val fab = hostTimerFabOverChrome(stripEnabled = true, showChrome = false).first
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fab.visibility == View.VISIBLE
        }
        val expected = fab.resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
        val lp = fab.layoutParams as CoordinatorLayout.LayoutParams
        assertEquals(expected, lp.bottomMargin)
    }

    private fun hostTimerFabOverChrome(
        stripEnabled: Boolean,
        showChrome: Boolean = true
    ): Pair<ExtendedFloatingActionButton, ComposeView> {
        val activity = composeRule.activity
        lateinit var fab: ExtendedFloatingActionButton
        lateinit var shell: ComposeView
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            fab = activity.findViewById(R.id.fab_main)
            shell = activity.findViewById(R.id.shell_destination_nav)
            shell.visibility = if (showChrome) View.VISIBLE else View.GONE
            shell.setViewTreeLifecycleOwner(activity)
            shell.setViewTreeViewModelStoreOwner(activity)
            shell.setViewTreeSavedStateRegistryOwner(activity)
            shell.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            shell.setContent {
                DreamDroidTheme {
                    if (showChrome) {
                        val state = TvMoviesHubState().apply {
                            selected = TvMoviesDestination.TIMER
                            nowPlayingStripEnabled = stripEnabled
                            nowPlayingHeadline = "Das Erste HD · Tagesschau"
                        }
                        TvMoviesShellChrome(state = state)
                    }
                    BindShellFab(
                        contentDescription = "New timer",
                        iconRes = R.drawable.ic_action_fab_add,
                        onClick = {},
                        text = "New timer"
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return fab to shell
    }

    private fun waitUntilDodged(fab: ExtendedFloatingActionButton, shell: ComposeView) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fab.visibility == View.VISIBLE &&
                shell.visibility == View.VISIBLE &&
                shell.height > 0 &&
                (fab.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin >
                fab.resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
        }
    }

    private fun assertFabAboveChrome(fab: ExtendedFloatingActionButton, shell: ComposeView) {
        val gap = fab.resources.getDimensionPixelSize(R.dimen.fab_margin_above_chrome)
        val lp = fab.layoutParams as CoordinatorLayout.LayoutParams
        assertEquals(gap + shell.height, lp.bottomMargin)
        assertTrue(
            "fab.bottom=${fab.bottom} shell.top=${shell.top} gap=$gap",
            fab.bottom <= shell.top
        )
    }
}
