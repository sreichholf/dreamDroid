package net.reichholf.dreamdroid.ui.current

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.CurrentServiceLoadResult
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [HubNowPlayingViewModel] lives on the hub back-stack entry: leaving the hub and
 * popping back paints the last-good service at once and does not fetch again.
 */
class HubNowPlayingRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as Application

    private var previousProfile: Profile? = null

    @Before
    fun seedProfileAndStrip() {
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(Profile().apply { id = 7 })
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true)
            .commit()
    }

    @After
    fun restoreProfile() {
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent(app)
        }
    }

    @Test
    fun reenteringHubKeepsLoadedNowPlayingWithoutReload() {
        val loaded = CurrentService(
            service = Service("1:0:1:1", "Box A"),
            now = Event(title = "News")
        )
        var loads = 0
        val sessions = SessionConnectionHolder().apply { onSuccess() }
        val factory = viewModelFactory {
            initializer {
                HubNowPlayingViewModel(app, sessions) {
                    loads += 1
                    CurrentServiceLoadResult(success = true, current = loaded, errorText = null)
                }
            }
        }
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
                        val model: HubNowPlayingViewModel = viewModel(factory = factory)
                        SideEffect { models += model }
                        HubNowPlaying(handle, reloadEpoch = 1, hubState, model)
                        SideEffect { headlines += hubState.nowPlayingHeadline }
                        Text(hubState.nowPlayingHeadline)
                    }
                    composable("other") { Text("Other page") }
                }
            }
        }
        composeRule.waitUntil(5_000) { headlines.lastOrNull() == "Box A · News" }
        val loadsBefore = composeRule.runOnIdle { loads }

        composeRule.runOnIdle { nav.navigate("other") }
        composeRule.onNodeWithText("Other page").assertExists()
        composeRule.runOnIdle {
            headlines.clear()
            nav.popBackStack()
        }
        composeRule.onNodeWithText("Box A · News").assertExists()

        composeRule.runOnIdle {
            assertEquals("re-entry must not fetch again", loadsBefore, loads)
            assertFalse("re-entry painted a blank state: $headlines", "Loading…" in headlines)
            assertEquals("Box A · News", headlines.first())
            assertSame(models.first(), models.last())
        }
    }
}

/**
 * [HubNowPlaying] only calls the handle when the sheet streams. Compose still compares
 * parameters with equals when it decides whether to skip.
 */
private fun unusedNavHandle(): PhoneNavHandle = Proxy.newProxyInstance(
    PhoneNavHandle::class.java.classLoader,
    arrayOf(PhoneNavHandle::class.java)
) { proxy, method, args ->
    when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "unusedNavHandle"
        else -> error("unexpected PhoneNavHandle.${method.name}")
    }
} as PhoneNavHandle
