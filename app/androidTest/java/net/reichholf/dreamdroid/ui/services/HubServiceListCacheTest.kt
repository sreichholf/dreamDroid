package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EpgNowNextLoadResult
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubServiceListCacheTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun offlineFirstOpenPaintsRosterWithoutHttp() = runBlocking {
        val dao = db.rosterDao()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val excluded = UserBouquetCache.excludedHubTabRefs(context)
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            favourites.reference,
            favourites.reference,
            listOf(channel),
            excluded
        )
        val httpCalls = AtomicInteger(0)
        val session = HubServiceListSession()
        session.context = context
        session.listState = ServiceListState()
        session.refresh = ComposeRefreshState()
        session.currentRef = favourites.reference
        session.rootRef = favourites.reference
        session.profileId = PROFILE
        session.rosterDao = dao
        session.epgDao = db.epgDao()
        session.isSessionOnline = { false }
        session.loadNowNext = { _, _ ->
            httpCalls.incrementAndGet()
            EpgNowNextLoadResult(false, emptyList(), "host_not_found")
        }
        session.loadAndApply(session.beginLoad(), forceRefresh = false)
        assertEquals(0, httpCalls.get())
        assertEquals(listOf("Das Erste HD"), session.listState!!.items.map { it.name })
    }

    @Test
    fun forceRefreshStillTriesHttpThenKeepsCache() = runBlocking {
        val dao = db.rosterDao()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val excluded = UserBouquetCache.excludedHubTabRefs(context)
        UserBouquetCache.replaceTabStrip(
            dao,
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        UserBouquetCache.persistRosterIfCacheable(
            dao,
            PROFILE,
            favourites.reference,
            favourites.reference,
            listOf(channel),
            excluded
        )
        val httpCalls = AtomicInteger(0)
        val session = HubServiceListSession()
        session.context = context
        session.listState = ServiceListState()
        session.refresh = ComposeRefreshState()
        session.currentRef = favourites.reference
        session.rootRef = favourites.reference
        session.profileId = PROFILE
        session.rosterDao = dao
        session.epgDao = db.epgDao()
        session.isSessionOnline = { false }
        session.loadNowNext = { _, _ ->
            httpCalls.incrementAndGet()
            EpgNowNextLoadResult(false, emptyList(), "host_not_found")
        }
        session.loadAndApply(session.beginLoad(), forceRefresh = true)
        assertEquals(1, httpCalls.get())
        assertEquals(listOf("Das Erste HD"), session.listState!!.items.map { it.name })
    }

    companion object {
        private const val PROFILE = 7
        private val favourites = Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites (TV)"
        )
        private val channel = ServiceNowNext(
            serviceReference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
            serviceName = "Das Erste HD"
        )
    }
}
