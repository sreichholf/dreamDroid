package net.reichholf.dreamdroid.ui.video

import android.content.Intent
import android.view.View
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone player shows the channel list or the infobar, never both; in portrait the
 * picture moves to the top while the list is open.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PhoneChannelListOverlayTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var profiles: ProfileRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()

    @Before
    fun startReceiver() {
        hiltRule.inject()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (!path.startsWith("/web/epgnow")) {
                    return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(eventList())
            }
        }
        server.start()
        profiles.setCurrent(
            Profile.getDefault().apply {
                name = "channel-list"
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
            }
        )
    }

    @After
    fun stopReceiver() {
        server.shutdown()
    }

    @Test
    fun theListReplacesTheInfobarUntilBackOrTheToggleClosesIt() {
        launchWithServices { scenario ->
            openList(scenario)
            scenario.onActivity { activity ->
                assertEquals(
                    View.GONE,
                    activity.findViewById<View>(R.id.compose_overlay).visibility
                )
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitInfobar(scenario)

            openList(scenario)
            scenario.onActivity { checkNotNull(it.overlay).onList() }
            awaitInfobar(scenario)
        }
    }

    @Test
    fun portraitDocksTheVideoWhileTheListIsOpen() {
        launchWithServices { scenario ->
            val portrait = windowLayout(scenario) == PhoneChannelLayout.Portrait
            openList(scenario)
            if (portrait) {
                composeRule.waitUntil(5_000) { containerTranslation(scenario) < 0f }
            } else {
                assertEquals(0f, containerTranslation(scenario), 0f)
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(5_000) { containerTranslation(scenario) == 0f }
            awaitInfobar(scenario)
        }
    }

    private fun launchWithServices(block: (ActivityScenario<VideoActivity>) -> Unit) {
        val intent = Intent(context, VideoActivity::class.java)
            .putExtra(VideoOverlayController.TITLE, "B")
            .putExtra(VideoOverlayController.SERVICE_REFERENCE, "1:0:1:b")
            .putExtra(VideoOverlayController.BOUQUET_REFERENCE, "1:7:1:bouquet")
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            lateinit var playback: VideoPlaybackViewModel
            scenario.onActivity { activity ->
                playback = ViewModelProvider(activity)[VideoPlaybackViewModel::class.java]
            }
            composeRule.waitUntil(10_000) { playback.uiState.value.services.size == 3 }
            block(scenario)
        }
    }

    /** Opening also restarts the auto-hide timer, so the overlay stays up for the next step. */
    private fun openList(scenario: ActivityScenario<VideoActivity>) {
        scenario.onActivity { checkNotNull(it.overlay).onList() }
        composeRule.waitUntil(5_000) { isShown(scenario, R.id.compose_zap_list) }
    }

    private fun awaitInfobar(scenario: ActivityScenario<VideoActivity>) {
        composeRule.waitUntil(5_000) { isShown(scenario, R.id.compose_overlay) }
        assertFalse(isShown(scenario, R.id.compose_zap_list))
    }

    private fun windowLayout(scenario: ActivityScenario<VideoActivity>): PhoneChannelLayout {
        var layout = PhoneChannelLayout.Portrait
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val density = activity.resources.displayMetrics.density
            layout = phoneChannelLayout(
                WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(
                    decor.width / density,
                    decor.height / density
                )
            )
        }
        return layout
    }

    private fun containerTranslation(scenario: ActivityScenario<VideoActivity>): Float {
        var translation = 0f
        scenario.onActivity {
            translation = it.findViewById<View>(R.id.player_container).translationY
        }
        return translation
    }

    private fun isShown(scenario: ActivityScenario<VideoActivity>, id: Int): Boolean {
        var shown = false
        scenario.onActivity { shown = it.findViewById<View>(id).isShown }
        return shown
    }

    private fun eventList(): String {
        val events = listOf("a" to "A", "b" to "B", "c" to "C").joinToString("") { (id, name) ->
            event(id, name, "News $name") + event(id, name, "Later $name")
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2eventlist>$events</e2eventlist>"
    }

    private fun event(id: String, name: String, title: String): String =
        "<e2event><e2eventid>$id</e2eventid><e2eventtitle>$title</e2eventtitle>" +
            "<e2eventservicereference>1:0:1:$id</e2eventservicereference>" +
            "<e2eventservicename>$name</e2eventservicename></e2event>"
}
