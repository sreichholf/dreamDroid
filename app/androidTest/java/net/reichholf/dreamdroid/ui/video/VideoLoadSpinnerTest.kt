package net.reichholf.dreamdroid.ui.video

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.video.PlayerEvent
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The load spinner stays while the controls hide on Playing, until the first picture, and a
 * reconnect's Opening brings it back even while it still fades out.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VideoLoadSpinnerTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var profiles: ProfileRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(404)
        }
        server.start()
        profiles.setCurrent(
            Profile.getDefault().apply {
                name = "spinner"
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
            }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun theSpinnerStaysAfterPlayingUntilThePictureIsReady() {
        // No ACTION_VIEW: the activity plays nothing, the test sends the player's events.
        val intent = Intent(context, VideoActivity::class.java)
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val overlay = checkNotNull(activity.overlay)
                overlay.showOverlays()
                overlay.onEvent(PlayerEvent.Opening)
                overlay.onEvent(PlayerEvent.Playing)
            }
            composeRule.waitUntil(5_000) { !isShown(scenario, R.id.overlay_root) }
            assertTrue(isShown(scenario, R.id.video_load_progress))

            scenario.onActivity { activity ->
                checkNotNull(activity.overlay).onEvent(PlayerEvent.PictureReady)
            }
            composeRule.waitUntil(5_000) { !isShown(scenario, R.id.video_load_progress) }
        }
    }

    @Test
    fun theSpinnerComesBackOnOpeningRightAfterThePicture() {
        val intent = Intent(context, VideoActivity::class.java)
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val overlay = checkNotNull(activity.overlay)
                overlay.onEvent(PlayerEvent.Opening)
                overlay.onEvent(PlayerEvent.PictureReady)
                overlay.onEvent(PlayerEvent.Opening)
            }
            composeRule.waitUntil(5_000) {
                spinnerAlpha(scenario) >= VideoOverlayController.overlayAlpha - 0.001f
            }
            assertTrue(isShown(scenario, R.id.video_load_progress))
        }
    }

    @Test
    fun volumeKeysReachTheSystemWhileTheOverlayIsHidden() {
        val intent = Intent(context, VideoActivity::class.java)
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            scenario.onActivity { checkNotNull(it.overlay).hideOverlays() }
            composeRule.waitUntil(5_000) { !isShown(scenario, R.id.overlay_root) }
            scenario.onActivity { activity ->
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
                assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, down))
                assertFalse(activity.findViewById<View>(R.id.overlay_root).isShown)
            }
        }
    }

    private fun spinnerAlpha(scenario: ActivityScenario<VideoActivity>): Float {
        var alpha = 0f
        scenario.onActivity { alpha = it.findViewById<View>(R.id.video_load_progress).alpha }
        return alpha
    }

    private fun isShown(scenario: ActivityScenario<VideoActivity>, id: Int): Boolean {
        var shown = false
        scenario.onActivity { shown = it.findViewById<View>(id).isShown }
        return shown
    }
}
