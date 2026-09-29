package net.reichholf.dreamdroid.ui.share

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.activities.ShareActivity
import net.reichholf.dreamdroid.room.AppDatabase
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShareActivityRetentionTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao by lazy { database.profileDao() }
    private val server = MockWebServer()
    private val release = CountDownLatch(1)
    private val plays = AtomicInteger(0)
    private val playStarted = CountDownLatch(1)

    @Before
    fun seedProfiles() {
        hiltRule.inject()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath != "/web/mediaplayerplay") {
                    return MockResponse().setBody("session")
                }
                plays.incrementAndGet()
                playStarted.countDown()
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
            runBlocking { dao.addProfile(profile) }
        }
    }

    @After
    fun deleteProfiles() {
        release.countDown()
        server.shutdown()
        runBlocking {
            dao.getProfiles()
                .filter { it.name?.startsWith("share-vm-") == true }
                .forEach { dao.deleteProfile(it) }
        }
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
            }
            awaitState(before) { state -> state.profiles.any { it.name == "share-vm-a" } }
            scenario.onActivity {
                val item = before.uiState.value.profiles.first { it.name == "share-vm-a" }
                before.onProfileClick(item)
            }
            assertTrue("play request", playStarted.await(10, TimeUnit.SECONDS))

            scenario.recreate()

            scenario.onActivity { activity ->
                val after = ViewModelProvider(activity)[ShareViewModel::class.java]
                assertSame(before, after)
                val state = after.uiState.value
                assertTrue(state.profiles.any { it.name == "share-vm-b" })
                assertTrue("send still in flight", state.sending)
            }
            release.countDown()
            awaitState(before) { it.finished }
            assertEquals(1, plays.get())
        }
    }

    private fun awaitState(viewModel: ShareViewModel, condition: (ShareUiState) -> Boolean) {
        runBlocking { withTimeout(10_000) { viewModel.uiState.first(condition) } }
    }
}
