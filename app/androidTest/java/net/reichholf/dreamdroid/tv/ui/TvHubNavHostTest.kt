package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.testReceiverProfile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Each TV route over the app's real Hilt graph, hosted in a debug Hilt activity. */
@HiltAndroidTest
class TvHubNavHostTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var profiles: ProfileRepository

    @Before
    fun prepare() {
        hiltRule.inject()
        profiles.setCurrent(testReceiverProfile())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
    }

    @Test
    fun multiEpgRouteShowsHost() {
        show(
            TvMultiEpg(
                bouquetRef = "1:7:1:0:0:0:0:0:0:0:Favourites",
                bouquetName = "Favourites"
            )
        )
        composeRule.onNodeWithTag("tv_multi_epg_screen").assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    @Test
    fun settingsRouteShowsSettingsScreen() {
        show(TvSettings)
        composeRule.onNodeWithText("Video Player").assertIsDisplayed()
        composeRule.onNodeWithText("Integrated video player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    @Test
    fun profilesRouteShowsProfilesHost() {
        show(TvProfiles)
        composeRule.onNodeWithTag("tv_profiles_list").assertExists()
        composeRule.onNodeWithTag("tv_profiles_add").assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertDoesNotExist()
    }

    private fun show(route: Any) {
        val activity = composeRule.activity
        composeRule.setContent {
            TvHubNavHost(
                activity = activity,
                onRecheckProfile = {},
                startDestination = route
            )
        }
    }
}
