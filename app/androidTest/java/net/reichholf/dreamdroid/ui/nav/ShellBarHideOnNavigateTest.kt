package net.reichholf.dreamdroid.ui.nav

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import org.junit.Assert.assertEquals
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
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            shell = activity.findViewById(R.id.shell_destination_nav)
            shell.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.TIMER_EDIT,
                controller,
                shell
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.GONE, shell.visibility)
        assertTrue(controller.content is ShellDestinationBarContent.Hidden)
    }

    @Test
    fun hubRouteLeavesShellBarVisible() {
        val activity = composeRule.activity
        lateinit var shell: ComposeView
        val controller = ShellDestinationBarController()
        composeRule.runOnUiThread {
            activity.setTheme(R.style.Theme_DreamDroid_Night)
            activity.setContentView(R.layout.dualpane)
            shell = activity.findViewById(R.id.shell_destination_nav)
            shell.visibility = View.VISIBLE
            controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
            applyShellDestinationBarForRoute(
                PhoneNavRoutes.HUB,
                controller,
                shell
            )
        }
        composeRule.waitForIdle()
        assertEquals(View.VISIBLE, shell.visibility)
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }
}
