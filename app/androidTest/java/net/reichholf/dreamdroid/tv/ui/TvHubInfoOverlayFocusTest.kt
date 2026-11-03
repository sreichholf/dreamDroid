package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The hub's INFO overlay over the real [ComposeTvHubApp]: Back closes it and the card that
 * opened it takes focus again, not the top bar.
 */
@HiltAndroidTest
class TvHubInfoOverlayFocusTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var sessions: SessionConnectionHolder

    private val nowNextXml = loadWebFixture("epgnownext.xml")

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                when (request.requestUrl?.encodedPath) {
                    "/web/getservices" -> MockResponse().setBody(SERVICES)
                    "/web/epgnownext" -> MockResponse().setBody(nowNextXml)
                    else -> MockResponse().setResponseCode(404)
                }
        }
        start()
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        profiles.setCurrent(
            Profile().apply {
                id = 9_997
                name = "TV focus receiver"
                host = server.hostName
                port = server.port
            }
        )
        sessions.onSuccess()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun infoOverlayDismissRestoresTheOpenerCard() {
        composeRule.setContent {
            TvHubNavHost(
                activity = composeRule.activity,
                onAddProfile = {},
                startDestination = TvHub
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("hub_service_card").fetchSemanticsNodes().isNotEmpty()
        }

        val card = composeRule.onAllNodesWithTag("hub_service_card")[0]
        card.requestFocus()
        card.performKeyInput { pressKey(Key.Info) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_service_timer_overlay").assertIsDisplayed()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("tv_service_timer_overlay").assertCountEquals(0)
        card.assertIsFocused()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        val SERVICES = "<e2servicelist>" +
            service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD") +
            service("1:0:1:6DCB:44D:1:C00000:0:0:0:", "ZDF HD") +
            "</e2servicelist>"

        fun service(reference: String, name: String) =
            "<e2service><e2servicereference>$reference</e2servicereference>" +
                "<e2servicename>$name</e2servicename></e2service>"
    }
}
