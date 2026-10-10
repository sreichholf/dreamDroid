package net.reichholf.dreamdroid.ui.services

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.testNavigator
import net.reichholf.dreamdroid.ui.nav.Hub
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.TimerServicePick
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.timers.TimerEditPaneHost
import net.reichholf.dreamdroid.ui.timers.TimerPaneViewModel
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The service picked for the timer form in the Timers detail pane reaches that form: the pick
 * route pops back to the hub and posts its result, which [HubActivityResults] hands to the
 * hub's [TimerPaneViewModel]. Real [net.reichholf.dreamdroid.ui.nav.PhoneNavigator] and
 * NavHost; the receiver answers nothing, so the form keeps its default choices.
 */
class HubTimerPaneServicePickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(404)
        }
        start()
    }
    private val profiles = memoryProfiles().apply {
        setCurrent(
            Profile().apply {
                id = 1
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
    }
    private val database = AppDatabase.inMemory(context)
    private val sessions = SessionConnectionHolder().apply { onSuccess() }
    private val clients =
        ReceiverApiFactory(context, profiles, EnigmaOkHttp(), WebIfCapabilitiesRepository())
    private val timers = TimerRepository(
        clients,
        profiles,
        database,
        ReceiverPluginsRepository(clients, profiles),
        sessions
    )

    // Cleared after the test, so the pane's choices job ends with it.
    private val viewModels = ViewModelStore()

    @After
    fun tearDown() {
        viewModels.clear()
        server.shutdown()
        database.close()
    }

    @Test
    fun aPickedServiceReachesTheTimerFormInThePane() {
        val handle = testNavigator(profiles, sessions)
        val pane = ViewModelProvider.create(
            viewModels,
            viewModelFactory {
                initializer { TimerPaneViewModel(SavedStateHandle(), timers, sessions) }
            }
        )[TimerPaneViewModel::class]
        var timerEdits = 0
        lateinit var navController: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = Hub) {
                    composable<Hub> {
                        HubActivityResults(
                            handle = handle,
                            onTimerEdited = { timerEdits++ },
                            onTimerServicePicked = pane::onServicePicked
                        )
                        Column { TimerEditPaneHost(handle, pane) }
                    }
                    composable<TimerServicePick> { Text(PICKER) }
                }
            }
        }
        composeRule.runOnIdle {
            handle.attachNavController(navController)
            pane.open(TIMER, isCreate = false)
        }
        composeRule.onNodeWithText("Das Erste HD").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle { assertTrue(handle.navigateToTimerServicePick()) }
        composeRule.onNodeWithText(PICKER).assertIsDisplayed()
        composeRule.runOnIdle {
            handle.deliverPickResult(
                Activity.RESULT_OK,
                Intent().putExtra(NavExtras.DATA, Service(ZDF_REF, "ZDF HD"))
            )
        }

        composeRule.waitUntil(5_000) { pane.uiState.value.timer?.reference == ZDF_REF }
        composeRule.onNodeWithText("ZDF HD").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("ZDF HD", pane.uiState.value.timer?.serviceName)
            assertEquals("a pick is not a timer edit", 0, timerEdits)
        }
    }

    private companion object {
        const val PICKER = "picker"
        const val ZDF_REF = "1:0:1:6DCB:44D:1:C00000:0:0:0:"
        val TIMER = TimerHelper.getInitialTimer().copy(
            name = "Sample",
            serviceName = "Das Erste HD",
            reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
            begin = "1893456000",
            end = "1893459600"
        )
    }
}
