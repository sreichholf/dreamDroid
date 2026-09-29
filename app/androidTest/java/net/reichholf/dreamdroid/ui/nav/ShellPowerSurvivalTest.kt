package net.reichholf.dreamdroid.ui.nav

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.testShellViewModel
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShellPowerSurvivalTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/web/powerstate") {
                    return MockResponse().setResponseCode(404)
                }
                started.complete(Unit)
                runBlocking { release.await() }
                return MockResponse().setBody(
                    "<e2powerstate><e2instandby>true</e2instandby></e2powerstate>"
                )
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
    private val hostState = SnackbarHostState()
    private lateinit var viewModel: ShellViewModel

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                testShellViewModel(profiles) as T
        }
        viewModel = ViewModelProvider(composeRule.activity, factory)[ShellViewModel::class.java]
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun powerToggleResultArrivesAfterRecreate() {
        showShell()
        composeRule.runOnUiThread {
            viewModel.setPowerState("0")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { started.isCompleted }

        composeRule.activityRule.scenario.recreate()
        showShell()
        composeRule.waitForIdle()
        release.complete(Unit)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Device is now in Standby-Mode")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Device is now in Standby-Mode").assertIsDisplayed()
    }

    private fun showShell() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                DreamDroidTheme {
                    CompositionLocalProvider(
                        LocalShellSnackbarHostState provides hostState
                    ) {
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
                        ShellSnackbarHost(hostState = hostState)
                    }
                }
            }
        }
    }
}
