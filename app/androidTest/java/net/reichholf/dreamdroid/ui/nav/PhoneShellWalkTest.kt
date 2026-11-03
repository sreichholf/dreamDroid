package net.reichholf.dreamdroid.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.helpers.isTelevision
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SHELL_TIMEOUT_MS = 10_000L

/**
 * Walks the phone shell as the app starts it: the hub's destination bar, the drawer to
 * Tools, then Back to the hub.
 *
 * The receiver is never asked. The test profile carries a cached device-info answer, so
 * the launch check reuses it and succeeds without a network. Without that cache a fresh
 * install has nothing to paint, and the shell opens the profile-check gate; without a
 * profile at all, [MainActivity] opens the setup assistant instead of the shell.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PhoneShellWalkTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var settings: SettingsRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var profile: Profile
    private var startScreenBefore: String = ""
    private var firstStartBefore: Boolean = false
    private var versionCodeBefore: Int = 0

    @Before
    fun setUp() {
        assumeFalse(context.isTelevision())
        hiltRule.inject()
        startScreenBefore = settings.current().startScreen
        firstStartBefore = settings.firstStart
        versionCodeBefore = settings.lastVersionCode
        settings.update { it.copy(startScreen = StartScreen.VALUE_SERVICES) }
        // The check's first success would otherwise land on Profiles, with the drawer open.
        settings.firstStart = false
        // Otherwise the shell opens the changelog dialog over the hub on this install.
        settings.lastVersionCode = BuildConfig.VERSION_CODE
        profile = Profile().apply {
            name = "Shell walk test"
            host = "127.0.0.1"
            port = 80
        }
        runBlocking {
            profiles.save(profile)
            profiles.setCurrent(checkNotNull(profile.id))
        }
        // setCurrent(id) publishes the row but not the process-start loaded flag.
        profiles.markLoaded()
        profiles.setDeviceInfo(profile, DeviceInfo(interfaceVersion = "1.7.5"))
    }

    @After
    fun tearDown() {
        if (::profiles.isInitialized && ::profile.isInitialized && profile.id != null) {
            runBlocking { profiles.delete(profile) }
        }
        // The launch flags are per-install: put back what this test found.
        if (::settings.isInitialized) {
            settings.update { it.copy(startScreen = startScreenBefore) }
            settings.firstStart = firstStartBefore
            settings.lastVersionCode = versionCodeBefore
        }
    }

    @Test
    fun drawerOpensToolsAndBackReturnsToTheHub() {
        val drawerOpen = context.getString(R.string.drawer_open)
        val tools = context.getString(R.string.tools)
        val hubTvTab = context.getString(R.string.tv)
        val deviceInfo = context.getString(R.string.device_info)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitText(hubTvTab)
            composeRule.onNodeWithText(hubTvTab).assertIsDisplayed()

            composeRule.onNodeWithContentDescription(drawerOpen).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(tools).assertIsDisplayed().performClick()

            awaitText(deviceInfo)
            awaitNoText(hubTvTab)
            composeRule.onNodeWithText(deviceInfo).assertIsDisplayed()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            awaitText(hubTvTab)
            awaitNoText(deviceInfo)
            composeRule.onNodeWithText(hubTvTab).assertIsDisplayed()
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(SHELL_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun awaitNoText(text: String) {
        composeRule.waitUntil(SHELL_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }
}
