package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvHubNavHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var previousProfile: Profile? = null

    @Before
    fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(Profile().apply { host = "127.0.0.1" })
    }

    @After
    fun restoreProfile() {
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        }
    }

    @Test
    fun multiEpgRouteShowsHost() {
        val activity = composeRule.activity
        composeRule.setContent {
            val hubViewModel = remember { idleHubViewModel() }
            TvHubNavHost(
                activity = activity,
                onRecheckProfile = {},
                hubViewModel = hubViewModel,
                startDestination = TvMultiEpg(
                    bouquetRef = "1:7:1:0:0:0:0:0:0:0:Favourites",
                    bouquetName = "Favourites"
                )
            )
        }
        composeRule.onNodeWithTag("tv_multi_epg_screen").assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    @Test
    fun settingsRouteShowsSettingsScreen() {
        val activity = composeRule.activity
        composeRule.setContent {
            val hubViewModel = remember { idleHubViewModel() }
            TvHubNavHost(
                activity = activity,
                onRecheckProfile = {},
                hubViewModel = hubViewModel,
                startDestination = TvSettings
            )
        }
        composeRule.onNodeWithText("Video Player").assertIsDisplayed()
        composeRule.onNodeWithText("Integrated video player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    @Test
    fun profilesRouteShowsProfilesHost() {
        val activity = composeRule.activity
        composeRule.setContent {
            val hubViewModel = remember { idleHubViewModel() }
            TvHubNavHost(
                activity = activity,
                onRecheckProfile = {},
                hubViewModel = hubViewModel,
                startDestination = TvProfiles
            )
        }
        composeRule.onNodeWithTag("tv_profiles_list").assertExists()
        composeRule.onNodeWithTag("tv_profiles_add").assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    private fun idleHubViewModel(): TvHubViewModel = TvHubViewModel(
        loader = object : TvHubLoader {
            override suspend fun browse(): TvHubBrowseResult = TvHubBrowseResult(
                rows = emptyList(),
                locations = emptyList(),
                errorText = null,
                usedCache = false
            )

            override suspend fun movies(dirname: String): TvHubMoviesResult = TvHubMoviesResult(
                movies = emptyList(),
                errorText = null,
                usedCache = false
            )
        },
        sessions = emptyFlow()
    )
}
