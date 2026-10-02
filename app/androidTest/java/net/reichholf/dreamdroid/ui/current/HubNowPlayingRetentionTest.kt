package net.reichholf.dreamdroid.ui.current

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.unusedNavHandle
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * [HubNowPlayingViewModel] lives on the hub back-stack entry: leaving the hub and
 * popping back paints the last-good service at once and does not fetch again.
 */
class HubNowPlayingRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val loads = AtomicInteger()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/web/getcurrent") {
                    return MockResponse().setResponseCode(404)
                }
                loads.incrementAndGet()
                return MockResponse().setBody(loadWebFixture("getcurrent.xml"))
            }
        }
        start()
    }
    private val profiles = memoryProfiles().apply {
        setCurrent(
            Profile().apply {
                id = 7
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
    }
    private val sessions = SessionConnectionHolder().apply { onSuccess() }
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context).apply {
        edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true)
            .commit()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun reenteringHubKeepsLoadedNowPlayingWithoutReload() {
        val receiver =
            ReceiverRepository(ReceiverApiFactory(context, profiles, EnigmaOkHttp()), profiles)
        val settings = SettingsRepository(preferences)
        val handle = unusedNavHandle()
        val headlines = mutableListOf<String>()
        val models = mutableListOf<HubNowPlayingViewModel>()
        lateinit var nav: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                nav = rememberNavController()
                NavHost(navController = nav, startDestination = "hub") {
                    composable("hub") {
                        val hubState = remember { TvMoviesHubState() }
                        val model = viewModel {
                            HubNowPlayingViewModel(
                                createSavedStateHandle(),
                                receiver,
                                profiles,
                                sessions,
                                settings
                            )
                        }
                        SideEffect { models += model }
                        HubNowPlaying(handle, reloadEpoch = 1, hubState, model)
                        SideEffect { headlines += hubState.nowPlayingHeadline }
                        Text(hubState.nowPlayingHeadline)
                    }
                    composable("other") { Text("Other page") }
                }
            }
        }
        composeRule.waitUntil(10_000) { headlines.lastOrNull() == HEADLINE }
        val loadsBefore = composeRule.runOnIdle { loads.get() }

        composeRule.runOnIdle { nav.navigate("other") }
        composeRule.onNodeWithText("Other page").assertExists()
        composeRule.runOnIdle {
            headlines.clear()
            nav.popBackStack()
        }
        composeRule.onNodeWithText(HEADLINE).assertExists()

        composeRule.runOnIdle {
            assertEquals("re-entry must not fetch again", loadsBefore, loads.get())
            assertFalse("re-entry painted a blank state: $headlines", "Loading…" in headlines)
            assertEquals(HEADLINE, headlines.first())
            assertSame(models.first(), models.last())
        }
    }

    private companion object {
        const val HEADLINE = "Das Erste HD · Tagesschau"
    }
}
