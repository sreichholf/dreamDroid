package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.PowerState
import net.reichholf.dreamdroid.enigma.PowerStateSetOutcome
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShellPowerSurvivalTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun powerToggleResultArrivesAfterRecreate() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val setPower: suspend (String, Context) -> PowerStateSetOutcome = { _, _ ->
            started.complete(Unit)
            release.await()
            PowerStateSetOutcome(
                success = true,
                powerState = PowerState(isRunning = false),
                errorText = null
            )
        }
        val viewModel = ViewModelProvider(
            composeRule.activity,
            ShellViewModel.factory(setPower = setPower)
        )[ShellViewModel::class.java]
        showSnackbarHost()
        composeRule.runOnUiThread {
            viewModel.setPowerState(PowerStateKeys.STATE_TOGGLE)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { started.isCompleted }

        composeRule.activityRule.scenario.recreate()
        showSnackbarHost()
        composeRule.waitForIdle()
        release.complete(Unit)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Device is now in Standby-Mode")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Device is now in Standby-Mode").assertIsDisplayed()
    }

    private fun showSnackbarHost() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                DreamDroidTheme {
                    ShellSnackbarHost()
                }
            }
        }
    }
}
