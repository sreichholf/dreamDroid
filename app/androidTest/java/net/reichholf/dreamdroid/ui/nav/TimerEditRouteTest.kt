package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.TimerListResult
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.testNavigator
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A listed timer opens in the editor however long the receiver's timer log is. Android's
 * regex reports no match once a route string outgrows its backtrack stack (about 117k
 * characters), and `navigate` then throws "destination cannot be found".
 */
class TimerEditRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path.orEmpty().startsWith("/web/timerlist")) {
                    MockResponse().setBody(TIMER_LIST)
                } else {
                    MockResponse().setResponseCode(404)
                }
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

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun aTimerWithALongLogOpensInTheEditor() {
        val result = runBlocking {
            withTimeout(10_000) {
                timers.timers(forceRefresh = true).first {
                    it is TimerListResult.Failed ||
                        (it is TimerListResult.Loaded && it.timers.isNotEmpty())
                }
            }
        }
        assertTrue("$result", result is TimerListResult.Loaded)
        val timer = (result as TimerListResult.Loaded).timers.first()

        val handle = testNavigator(profiles, sessions)
        lateinit var navController: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = Hub) {
                    composable<Hub> { Text(HUB) }
                    composable<TimerEdit> { Text(it.toRoute<TimerEdit>().name) }
                }
            }
        }
        composeRule.onNodeWithText(HUB).assertIsDisplayed()
        composeRule.runOnIdle {
            handle.attachNavController(navController)
            assertTrue(handle.navigateToTimerEdit(timer, create = false))
        }

        composeRule.onNodeWithText(NAME).assertIsDisplayed()
    }

    private companion object {
        const val HUB = "hub"
        const val NAME = "Navy & CIS: 100% #1 a=b"

        // About a year of runs of a daily timer; well past the regex limit once encoded.
        val LOG = List(5_000) { "(${1476254301 + it * 86_400}, 15, 'record time changed')" }
            .joinToString(", ", "[", "]")

        val TIMER_LIST = loadWebFixture("timerlist.xml")
            .replace(
                "<e2name>Navy CIS: L.A.</e2name>",
                "<e2name>Navy &amp; CIS: 100% #1 a=b</e2name>"
            )
            .replace(
                "<e2description>Kein Rauch ohne Feuer</e2description>",
                "<e2description>Kein Rauch\nohne Feuer</e2description>"
            )
            .replace(
                "<e2logentries>[(1476254301, 15, 'record time changed')]</e2logentries>",
                "<e2logentries>$LOG</e2logentries>"
            )
    }
}
