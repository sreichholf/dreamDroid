package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.LARGE_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.testNavigator
import net.reichholf.dreamdroid.ui.compose.LIST_DETAIL_DETAIL_PANE_TAG
import net.reichholf.dreamdroid.ui.compose.LIST_DETAIL_EXTRA_PANE_TAG
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The services hub's panes on wide windows, on the real Hilt graph and a fake receiver: a
 * channel's EPG beside the list, an event of it in the extra pane, and a row's current event
 * in place of the EPG. Which pane closes when, and the order back closes them in.
 */
@HiltAndroidTest
class HubServiceListPanesTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var sessions: SessionConnectionHolder

    @Inject
    lateinit var settings: SettingsRepository

    private var instantZapBefore = false

    // Loaded here: the server answers on its own thread, which may lack a context class loader.
    private val nowNextXml = loadWebFixture("epgnownext.xml")
    private val serviceEpgXml = loadWebFixture("epgservice.xml")

    // The bouquet is not in a Room tab strip, so its list loads once: a second, cached emission
    // would close a row menu the test just opened.
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                when (request.requestUrl?.encodedPath) {
                    "/web/getservices" -> MockResponse().setBody(SERVICES)

                    "/web/epgnownext", "/web/epgnow" ->
                        MockResponse().setBody(nowNextXml)

                    "/web/epgservice" -> MockResponse().setBody(serviceEpgXml)

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
                id = 9_998
                name = "Panes receiver"
                host = server.hostName
                port = server.port
            }
        )
        sessions.onSuccess()
        // A tap opens the row menu only without instant zap.
        val (before, _) = settings.update { it.copy(instantZap = false) }
        instantZapBefore = before.instantZap
    }

    @After
    fun tearDown() {
        settings.update { it.copy(instantZap = instantZapBefore) }
        server.shutdown()
    }

    @Test
    fun theEventsCloseButtonClosesOnlyTheEventAndBackThenClosesTheEpg() {
        show(EXPANDED_WINDOW_WIDTH)
        openFromRowMenu(DAS_ERSTE, R.string.browse_epg)
        openEpgEvent()

        extraPaneClose().performClick()

        awaitGone(LIST_DETAIL_EXTRA_PANE_TAG)
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()

        openEpgEvent()
        pressBack()
        awaitGone(LIST_DETAIL_EXTRA_PANE_TAG)
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()

        pressBack()
        awaitGone(LIST_DETAIL_DETAIL_PANE_TAG)
    }

    @Test
    fun onThreePanesTheEventsCloseButtonKeepsTheEpg() {
        show(LARGE_WINDOW_WIDTH)
        openFromRowMenu(DAS_ERSTE, R.string.browse_epg)
        openEpgEvent()
        composeRule.onNode(row(ZDF)).assertIsDisplayed()

        extraPaneClose().performClick()

        awaitGone(LIST_DETAIL_EXTRA_PANE_TAG)
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
    }

    @Test
    fun onThreePanesAnotherChannelsEpgClosesTheOpenEvent() {
        show(LARGE_WINDOW_WIDTH)
        openFromRowMenu(DAS_ERSTE, R.string.browse_epg)
        openEpgEvent()

        openFromRowMenu(ZDF, R.string.browse_epg)

        awaitGone(LIST_DETAIL_EXTRA_PANE_TAG)
        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
    }

    @Test
    fun aRowsCurrentEventTakesThePlaceOfTheEpg() {
        show(EXPANDED_WINDOW_WIDTH)
        openFromRowMenu(DAS_ERSTE, R.string.browse_epg)
        awaitNode(hasText(EPG_EVENT) and hasClickAction())

        openFromRowMenu(ZDF, R.string.current_event)

        awaitNode(hasText(ZDF_NOW) and inPane(LIST_DETAIL_DETAIL_PANE_TAG))
        awaitGone(hasText(EPG_EVENT) and hasClickAction())
        pressBack()
        awaitGone(LIST_DETAIL_DETAIL_PANE_TAG)
    }

    private fun show(width: Dp) {
        val handle = testNavigator(profiles, sessions)
        composeRule.setContent {
            DreamDroidTheme {
                WithWindowSize(width) {
                    HubServiceListPage(handle = handle, bouquetRef = BOUQUET, bouquetName = "TV")
                }
            }
        }
        awaitNode(row(DAS_ERSTE))
    }

    private fun openFromRowMenu(channel: String, action: Int) {
        composeRule.onNode(row(channel)).performClick()
        val label = composeRule.activity.getString(action)
        awaitNode(hasText(label) and hasClickAction())
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun openEpgEvent() {
        val event = hasText(EPG_EVENT) and hasClickAction()
        awaitNode(event)
        composeRule.onNode(event).performClick()
        awaitNode(hasTestTag(LIST_DETAIL_EXTRA_PANE_TAG))
    }

    private fun extraPaneClose() = composeRule.onNode(
        hasContentDescription(composeRule.activity.getString(R.string.close)) and
            inPane(LIST_DETAIL_EXTRA_PANE_TAG)
    )

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun row(channel: String) = hasText(channel) and hasClickAction() and
        !inPane(LIST_DETAIL_DETAIL_PANE_TAG) and !inPane(LIST_DETAIL_EXTRA_PANE_TAG)

    private fun inPane(tag: String) = hasAnyAncestor(hasTestTag(tag))

    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(tag: String) = awaitGone(hasTestTag(tag))

    private fun awaitGone(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.panes.tv\" ORDER BY bouquet"
        const val DAS_ERSTE = "Das Erste HD"
        const val ZDF = "ZDF HD"
        const val ZDF_NOW = "Sport Now"
        const val EPG_EVENT = "Tagesschau"
        val SERVICES = "<e2servicelist>" +
            service("1:0:1:6DCA:44D:1:C00000:0:0:0:", DAS_ERSTE) +
            service("1:0:1:6DCB:44D:1:C00000:0:0:0:", ZDF) +
            "</e2servicelist>"

        fun service(reference: String, name: String) =
            "<e2service><e2servicereference>$reference</e2servicereference>" +
                "<e2servicename>$name</e2servicename></e2service>"
    }
}
