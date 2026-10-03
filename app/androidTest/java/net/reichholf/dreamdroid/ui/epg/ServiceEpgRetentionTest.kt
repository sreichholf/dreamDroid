package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.testNavigator
import net.reichholf.dreamdroid.ui.nav.EpgSearch
import net.reichholf.dreamdroid.ui.nav.Hub
import net.reichholf.dreamdroid.ui.nav.ServiceEpg
import net.reichholf.dreamdroid.ui.nav.navigateToServiceEpg
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceEpgRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val server = MockWebServer().apply {
        enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))
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
    private val database = AppDatabase.inMemory(
        InstrumentationRegistry.getInstrumentation().targetContext
    )
    private val clients = ReceiverApiFactory(
        InstrumentationRegistry.getInstrumentation().targetContext,
        profiles,
        EnigmaOkHttp(),
        WebIfCapabilitiesRepository()
    )
    private val sessions = SessionConnectionHolder().apply { onSuccess() }
    private val repository = EpgRepository(
        clients,
        profiles,
        database,
        sessions,
        ServiceRepository(
            InstrumentationRegistry.getInstrumentation().targetContext,
            clients,
            profiles,
            database,
            sessions
        )
    )
    private val timers = TimerRepository(clients, profiles, database)
    private val autoTimerScope = MainScope()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @After
    fun tearDown() {
        autoTimerScope.cancel()
        server.shutdown()
        database.close()
    }

    @Test
    fun popBackFromEventDetailKeepsListWithoutReload() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val handle = testNavigator(profiles, sessions)
        val viewModels = mutableListOf<ServiceEpgViewModel>()
        lateinit var navController: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = Hub) {
                    composable<Hub> { Text("Hub") }
                    composable<ServiceEpg> {
                        val viewModel = viewModel {
                            ServiceEpgViewModel(createSavedStateHandle(), repository, sessions)
                        }
                        val detail = viewModel {
                            EpgEventDetailViewModel(
                                createSavedStateHandle(),
                                timers,
                                AutoTimerRepository(clients, profiles, autoTimerScope),
                                sessions
                            )
                        }
                        viewModels += viewModel
                        ServiceEpgDestination(
                            handle = handle,
                            viewModel = viewModel,
                            detailViewModel = detail
                        )
                    }
                    composable<EpgSearch> {
                        Text("Search")
                    }
                }
            }
        }
        composeRule.runOnIdle {
            handle.attachNavController(navController)
            navController.navigateToServiceEpg(SERVICE_REF, "Das Erste HD")
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Tagesschau").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(app.getString(R.string.similar)).performClick()
        composeRule.onNodeWithText("Search").assertIsDisplayed()

        composeRule.runOnIdle { handle.popNavBackStack() }

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("pop back must not start a fresh load", 1, server.requestCount)
            assertSame(viewModels.first(), viewModels.last())
        }
        val url = server.takeRequest().requestUrl!!
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(SERVICE_REF, url.queryParameter("sRef"))
    }

    private companion object {
        const val SERVICE_REF = "1:0:19:283D:3FB:1:C00000:0:0:0:"
    }
}
