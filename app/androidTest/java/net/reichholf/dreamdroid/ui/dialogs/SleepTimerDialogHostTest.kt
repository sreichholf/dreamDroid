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
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.SleepTimerNavArgs
import net.reichholf.dreamdroid.ui.nav.rememberSleepTimerNavArgs
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Sleep timer Navigation `dialog` must snapshot consume-once args so recomposition does
 * not reset the form to [SleepTimerNavArgs.defaults] (90 min / off / standby).
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
    fun queuedArgsSurviveRecomposition() {
        val queued = SleepTimerNavArgs(15, false, SleepTimer.ACTION_SHUTDOWN)
        val queue = SleepTimerArgsQueue(queued)
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
                            navController.navigate(PhoneNavRoutes.SLEEP_TIMER)
                        }
                    }
                    dialog(PhoneNavRoutes.SLEEP_TIMER) {
                        @Suppress("UNUSED_VARIABLE")
                        val recompositionGate = recomposeTick.intValue
                        val args = rememberSleepTimerNavArgs { queue.consume() }
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
        composeRule.runOnIdle {
            assertEquals(SleepTimerNavArgs.defaults(), queue.consume())
            recomposeTick.intValue++
        }
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

/** Mirrors [net.reichholf.dreamdroid.ui.nav.PhoneNavHandle.consumeSleepTimerArgs]. */
private class SleepTimerArgsQueue(initial: SleepTimerNavArgs) {
    private var pending: SleepTimerNavArgs? = initial

    fun consume(): SleepTimerNavArgs {
        val args = pending ?: SleepTimerNavArgs.defaults()
        pending = null
        return args
    }
}
