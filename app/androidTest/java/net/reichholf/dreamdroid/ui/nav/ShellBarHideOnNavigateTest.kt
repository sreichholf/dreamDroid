package net.reichholf.dreamdroid.ui.nav

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Leaving the hub must hide `shell_destination_nav` on the destination change,
 * not after Hub's [RegisterShellDestinationBar] disposes.
 */
class ShellBarHideOnNavigateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun timerEditRouteHidesShellBarImmediately() {
        val activity = composeRule.activity
        lateinit var shell: ComposeView
        var rail: ComposeView? = null
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            shell = activity.findViewById(R.id.shell_destination_nav)
            rail = activity.findViewById(R.id.shell_destination_rail)
            shell.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.TIMER_EDIT,
                controller,
                shell,
                rail
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.GONE, shell.visibility)
        val hiddenRail = rail
        if (hiddenRail != null) {
            assertEquals(View.GONE, hiddenRail.visibility)
        }
        assertTrue(controller.content is ShellDestinationBarContent.Hidden)
    }

    @Test
    fun hubRouteLeavesShellBarVisible() {
        val activity = composeRule.activity
        lateinit var shell: ComposeView
        var rail: ComposeView? = null
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            shell = activity.findViewById(R.id.shell_destination_nav)
            rail = activity.findViewById(R.id.shell_destination_rail)
            shell.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.HUB,
                controller,
                shell,
                rail
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.VISIBLE, shell.visibility)
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }

    @Test
    fun hubRouteLeavesTabletRailForHost() {
        val activity = composeRule.activity
        lateinit var shell: ComposeView
        lateinit var rail: ComposeView
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(inflateTabletDualpane(activity))
            shell = activity.findViewById(R.id.shell_destination_nav)
            rail = activity.findViewById(R.id.shell_destination_rail)
            assertNotNull(rail)
            shell.visibility = View.VISIBLE
            rail.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.HUB,
                controller,
                shell,
                rail
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.VISIBLE, shell.visibility)
        assertEquals(View.VISIBLE, rail.visibility)
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }

    @Test
    fun timerEditRouteHidesTabletRail() {
        val activity = composeRule.activity
        lateinit var shell: ComposeView
        lateinit var rail: ComposeView
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(inflateTabletDualpane(activity))
            shell = activity.findViewById(R.id.shell_destination_nav)
            rail = activity.findViewById(R.id.shell_destination_rail)
            assertNotNull(rail)
            shell.visibility = View.VISIBLE
            rail.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.TIMER_EDIT,
                controller,
                shell,
                rail
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.GONE, shell.visibility)
        assertEquals(View.GONE, rail.visibility)
        assertTrue(controller.content is ShellDestinationBarContent.Hidden)
    }

    private fun inflateTabletDualpane(base: ComponentActivity): View {
        val config = Configuration(base.resources.configuration)
        config.smallestScreenWidthDp = 720
        val ctx = ContextThemeWrapper(
            base.createConfigurationContext(config),
            R.style.Theme_DreamDroid_Night
        )
        return LayoutInflater.from(ctx).inflate(R.layout.dualpane, null, false)
    }
}
