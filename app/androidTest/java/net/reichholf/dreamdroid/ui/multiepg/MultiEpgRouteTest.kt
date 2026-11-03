package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.testNavigator
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.nav.Hub
import net.reichholf.dreamdroid.ui.nav.MultiEpg
import net.reichholf.dreamdroid.ui.nav.PhoneNavigator
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The hub tab a bouquet was opened under reaches the MultiEPG persist gate through the
 * navigator route and [MultiEpgDestination]'s load. A nested folder under the tab is written
 * to Room only when that root survives the trip.
 */
@HiltAndroidTest
class MultiEpgRouteTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var sessions: SessionConnectionHolder

    @Inject
    lateinit var database: AppDatabase

    // Loaded here: the server answers on its own thread, which may lack a context class loader.
    private val servicesXml = loadWebFixture("getservices.xml")
    private val epgMultiXml = loadWebFixture("epgmulti.xml")
    private val timerListXml = loadWebFixture("timerlist.xml")

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.encodedPath) {
                    "/web/getservices" -> MockResponse().setBody(servicesXml)
                    "/web/epgmulti" -> MockResponse().setBody(epgMultiXml)
                    "/web/timerlist" -> MockResponse().setBody(timerListXml)
                    else -> MockResponse().setResponseCode(404)
                }
        }
        start()
    }

    private val navigatorPrefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("navigator-test", 0)

    private lateinit var handle: PhoneNavigator

    @Before
    fun setUp() {
        hiltRule.inject()
        profiles.setCurrent(
            Profile().apply {
                id = PROFILE_ID
                name = "MultiEPG route"
                host = server.hostName
                port = server.port
                defaultBouquetTv = FOLDER
                defaultBouquetTvName = "Satellites"
                defaultParentBouquetTv = TAB
                defaultParentBouquetTvName = "Favourites"
            }
        )
        sessions.onSuccess()
        runBlocking {
            database.epgDao().deleteAllForProfile(PROFILE_ID)
            database.rosterDao().deleteAllForProfile(PROFILE_ID)
            database.rosterDao().replaceTabStrip(
                PROFILE_ID,
                "TV",
                listOf(BouquetTabEntity(PROFILE_ID, "TV", 0, TAB, "Favourites"))
            )
        }
        handle = testNavigator(profiles, sessions)
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking {
            database.epgDao().deleteAllForProfile(PROFILE_ID)
            database.rosterDao().deleteAllForProfile(PROFILE_ID)
        }
    }

    @Test
    fun drawerMultiEpgKeepsTheTabRoot() {
        DrawerEpgMode.save(navigatorPrefs, DrawerEpgMode.MULTI)
        val anchor = MultiEpgNowClock.sec()
        val nav = hostNavHost()

        composeRule.runOnIdle {
            handle.attachNavController(nav)
            assertTrue(handle.navigateToDrawerEpg())
            assertEquals(TAB, nav.currentBackStackEntry?.toRoute<MultiEpg>()?.tabRootRef)
        }

        awaitFolderChunk(anchor)
    }

    @Test
    fun drawerListEpgCarriesTheTabRoot() {
        DrawerEpgMode.save(navigatorPrefs, DrawerEpgMode.LIST)
        val nav = hostNavHost()

        composeRule.runOnIdle {
            handle.attachNavController(nav)
            assertTrue(handle.navigateToDrawerEpg())
            val route = nav.currentBackStackEntry?.toRoute<Epg>()
            assertEquals(FOLDER, route?.serviceRef)
            assertEquals(TAB, route?.tabRootRef)
        }
    }

    @Test
    fun multiEpgRouteKeepsTheTabRoot() {
        val anchor = MultiEpgNowClock.sec()
        val nav = hostNavHost()

        composeRule.runOnIdle {
            handle.attachNavController(nav)
            assertTrue(handle.navigateToMultiEpg(FOLDER, "Satellites", tabRootRef = TAB))
            assertEquals(TAB, nav.currentBackStackEntry?.toRoute<MultiEpg>()?.tabRootRef)
        }

        awaitFolderChunk(anchor)
    }

    private fun hostNavHost(): NavHostController {
        lateinit var controller: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                controller = rememberNavController()
                NavHost(navController = controller, startDestination = Hub) {
                    composable<Hub> { Text(HUB) }
                    composable<Epg> { Text(LIST_EPG) }
                    composable<MultiEpg> { entry ->
                        MultiEpgDestination(handle = handle, route = entry.toRoute())
                    }
                }
            }
        }
        return controller
    }

    /**
     * The destination's load wrote the folder's chunk under the tab root. A run that crosses
     * the UTC chunk boundary starts in either the chunk of [anchor] or the one after it.
     */
    private fun awaitFolderChunk(anchor: Long) {
        val first = MultiEpgWindows.chunkContaining(anchor).startSec
        val second = first + MultiEpgWindows.CHUNK_SECONDS
        composeRule.waitUntil(10_000L) {
            runBlocking {
                database.epgDao().getChunk(PROFILE_ID, FOLDER, first) != null ||
                    database.epgDao().getChunk(PROFILE_ID, FOLDER, second) != null
            }
        }
    }

    private companion object {
        const val PROFILE_ID = 9_999
        const val TAB =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.tab.tv\" ORDER BY bouquet"
        const val FOLDER = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
        const val HUB = "hub"
        const val LIST_EPG = "epg"
    }
}
