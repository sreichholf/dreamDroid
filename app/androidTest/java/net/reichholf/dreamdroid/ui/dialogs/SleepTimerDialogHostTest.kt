package net.reichholf.dreamdroid.ui.dialogs

import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.fragment.SleepTimerNavArgs
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.rememberSleepTimerNavArgs
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // Activate is a selectable Checkbox row (Selected), not ToggleableState.
        composeRule.onNodeWithText("Activate").assertIsNotSelected()
        composeRule.onNodeWithText("Shutdown").assertIsSelected()
        composeRule.onNodeWithText("Standby").assertIsNotSelected()
        composeRule.runOnIdle {
            val picker = findNumberPicker()
            assertNotNull("sleep timer minutes picker", picker)
            assertEquals(queued.minutes, picker!!.value)
        }
    }
}

/** Mirrors [net.reichholf.dreamdroid.fragment.PhoneNavHostFragment.consumeSleepTimerArgs]. */
private class SleepTimerArgsQueue(initial: SleepTimerNavArgs) {
    private var pending: SleepTimerNavArgs? = initial

    fun consume(): SleepTimerNavArgs {
        val args = pending ?: SleepTimerNavArgs.defaults()
        pending = null
        return args
    }
}

private fun findNumberPicker(): NumberPicker? {
    val wmgClass = Class.forName("android.view.WindowManagerGlobal")
    val instance = wmgClass.getMethod("getInstance").invoke(null)
    val viewsField = wmgClass.getDeclaredField("mViews")
    viewsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val roots = viewsField.get(instance) as List<View>
    for (root in roots) {
        findNumberPicker(root)?.let { return it }
    }
    return null
}

private fun findNumberPicker(root: View): NumberPicker? {
    if (root is NumberPicker) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            findNumberPicker(root.getChildAt(i))?.let { return it }
        }
    }
    return null
}
