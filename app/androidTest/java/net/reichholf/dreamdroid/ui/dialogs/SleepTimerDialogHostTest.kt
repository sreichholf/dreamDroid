package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.nav.SleepTimerNavArgs
import net.reichholf.dreamdroid.ui.nav.SleepTimerRoute
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Sleep timer Navigation `dialog` reads minutes, enabled, and action from the route,
 * so recomposition does not reset the form to [SleepTimerNavArgs.defaults].
 */
class SleepTimerDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun routeArgsSurviveRecomposition() {
        val queued = SleepTimerNavArgs(15, false, SleepTimer.ACTION_SHUTDOWN)
        val recomposeTick = mutableIntStateOf(0)
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        LaunchedEffect(Unit) {
                            navController.navigate(
                                SleepTimerRoute(
                                    minutes = queued.minutes,
                                    enabled = queued.enabled,
                                    action = queued.action
                                )
                            )
                        }
                    }
                    dialog<SleepTimerRoute> { entry ->
                        @Suppress("UNUSED_VARIABLE")
                        val recompositionGate = recomposeTick.intValue
                        val args = entry.toRoute<SleepTimerRoute>()
                        SleepTimerDialog(
                            initialMinutes = args.minutes,
                            initialEnabled = args.enabled,
                            initialAction = args.action.ifEmpty {
                                defaultSleepTimerAction()
                            },
                            onDismiss = { navController.popBackStack() },
                            onSave = { _, _, _ -> }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertQueuedForm(queued)
        composeRule.runOnIdle { recomposeTick.intValue++ }
        composeRule.waitForIdle()
        assertQueuedForm(queued)
    }

    private fun assertQueuedForm(queued: SleepTimerNavArgs) {
        composeRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Activate").assertIsOff()
        composeRule.onNodeWithText("Shutdown").assertIsSelected()
        composeRule.onNodeWithText("Standby").assertIsNotSelected()
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_TAG)
            .assertIsDisplayed()
            .assertTextContains(queued.minutes.toString())
    }
}
