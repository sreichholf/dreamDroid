package net.reichholf.dreamdroid.ui.video

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [VideoPlaybackViewModel] is activity-scoped: a recreate keeps the zap list and the
 * zap position, and the new overlay paints them while the next now/next load hangs.
 */
@RunWith(AndroidJUnit4::class)
class VideoPlaybackRetentionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()
    private val release = CountDownLatch(1)
    private val loads = AtomicInteger(0)
    private var previousProfile: Profile? = null

    @Before
    fun startReceiver() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (!path.startsWith("/web/epgnow")) {
                    return MockResponse().setResponseCode(404)
                }
                if (loads.incrementAndGet() > 1) {
                    release.await(60, TimeUnit.SECONDS)
                }
                return MockResponse().setBody(eventList(paired = path == "/web/epgnownext"))
            }
        }
        server.start()
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(
            Profile.getDefault().apply {
                name = "video-vm"
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
            }
        )
    }

    @After
    fun stopReceiver() {
        release.countDown()
        server.shutdown()
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent(context)
        }
    }

    @Test
    fun recreateKeepsZapListAndPositionWithoutWaitingForReload() {
        val intent = Intent(context, VideoActivity::class.java)
            .putExtra(VideoOverlayController.TITLE, "Intent title")
            .putExtra(VideoOverlayController.SERVICE_REFERENCE, "1:0:1:b")
            .putExtra(VideoOverlayController.BOUQUET_REFERENCE, "1:7:1:bouquet")
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            lateinit var before: VideoPlaybackViewModel
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[VideoPlaybackViewModel::class.java]
            }
            composeRule.waitUntil(10_000) { before.session.value.services.size == 3 }
            assertEquals(1, before.session.value.currentIndex)

            scenario.recreate()

            composeRule.waitUntil(10_000) { loads.get() == 2 }
            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[VideoPlaybackViewModel::class.java]
                assertSame(before, after)
                val session = after.session.value
                assertEquals(listOf("A", "B", "C"), session.services.map { it.serviceName })
                assertEquals("1:0:1:b", session.serviceRef)
                assertEquals(1, session.currentIndex)
            }
            composeRule.onNodeWithText("News B").assertExists()
        }
    }

    private fun eventList(paired: Boolean): String {
        val events = listOf("a" to "A", "b" to "B", "c" to "C").joinToString("") { (id, name) ->
            val now = event(id, name, "News $name")
            if (paired) now + event(id, name, "Later $name") else now
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2eventlist>$events</e2eventlist>"
    }

    private fun event(id: String, name: String, title: String): String =
        "<e2event><e2eventid>$id</e2eventid><e2eventtitle>$title</e2eventtitle>" +
            "<e2eventservicereference>1:0:1:$id</e2eventservicereference>" +
            "<e2eventservicename>$name</e2eventservicename></e2event>"
}
