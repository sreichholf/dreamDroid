package net.reichholf.dreamdroid.ui.share

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.activities.ShareActivity
import net.reichholf.dreamdroid.room.AppDatabase
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareActivityRetentionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao = AppDatabase.profilesBlocking(context)
    private val server = MockWebServer()
    private val release = CountDownLatch(1)
    private val plays = AtomicInteger(0)

    @Before
    fun seedProfiles() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/web/mediaplayerplay") {
                    return MockResponse().setBody("session")
                }
                plays.incrementAndGet()
                release.await(60, TimeUnit.SECONDS)
                return MockResponse().setBody(
                    "<e2simplexmlresult><e2state>True</e2state>" +
                        "<e2statetext>ok</e2statetext></e2simplexmlresult>"
                )
            }
        }
        server.start()
        for (name in listOf("share-vm-a", "share-vm-b")) {
            val profile = Profile.getDefault().apply {
                this.name = name
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
            }
            dao.addProfile(profile)
        }
    }

    @After
    fun deleteProfiles() {
        release.countDown()
        server.shutdown()
        dao.getProfiles()
            .filter { it.name?.startsWith("share-vm-") == true }
            .forEach { dao.deleteProfile(it) }
    }

    @Test
    fun recreateKeepsListAndInFlightSendWithoutResending() {
        val intent = Intent(context, ShareActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, "http://example.com/stream.ts")
            .putExtra("title", "Stream")
        ActivityScenario.launch<ShareActivity>(intent).use { scenario ->
            lateinit var before: ShareViewModel
            scenario.onActivity { activity ->
                before = ViewModelProvider(activity)[ShareViewModel::class.java]
                val item = before.listState.profiles.first { it.name == "share-vm-a" }
                before.onProfileClick(item)
            }
            waitUntil { plays.get() == 1 }

            scenario.recreate()

            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[ShareViewModel::class.java]
                assertSame(before, after)
                assertTrue(after.listState.profiles.any { it.name == "share-vm-b" })
                assertNotNull("send still in flight", after.listState.progress)
            }
            release.countDown()
            waitUntil { before.finished }
            assertEquals(1, plays.get())
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(50)
        }
    }
}
