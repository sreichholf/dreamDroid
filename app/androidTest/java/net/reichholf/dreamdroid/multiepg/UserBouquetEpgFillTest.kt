package net.reichholf.dreamdroid.multiepg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserBouquetEpgFillTest {
    private lateinit var db: AppDatabase
    private lateinit var excluded: Set<String>
    private val fetches = AtomicInteger(0)

    private val favourites = Service(
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
        "Favourites (TV)"
    )
    private val nestedFolder =
        "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
    private val channel = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
    private val nowSec = 1_893_456_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        excluded = UserBouquetCache.excludedHubTabRefs(context)
        fetches.set(0)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun favouritesTabDirectoryWritesChunk() = runBlocking {
        seedFavouritesTab()
        val sync = syncReturning(channelEvent(channel))
        UserBouquetEpgFill.ensureNowChunk(
            sync = sync,
            rosterDao = db.rosterDao(),
            profileId = PROFILE,
            containerRef = favourites.reference,
            tabRootRef = favourites.reference,
            excludedTabRefs = excluded,
            unixSec = nowSec
        )
        val chunk = MultiEpgWindows.chunkContaining(nowSec)
        val meta = db.epgDao().getChunk(PROFILE, favourites.reference, chunk.startSec)
        assertTrue(meta != null)
        val stored = db.epgDao().eventsOverlapping(
            PROFILE,
            favourites.reference,
            chunk.startSec,
            chunk.endSec
        )
        assertEquals(listOf(channel), stored.map { it.serviceRef })
        assertEquals(1, fetches.get())
    }

    @Test
    fun nestedFolderWritesChildEventsWithFolderBouquetRef() = runBlocking {
        seedFavouritesTab()
        val sync = syncReturning(channelEvent(channel))
        UserBouquetEpgFill.ensureNowChunk(
            sync = sync,
            rosterDao = db.rosterDao(),
            profileId = PROFILE,
            containerRef = nestedFolder,
            tabRootRef = favourites.reference,
            excludedTabRefs = excluded,
            unixSec = nowSec
        )
        val chunk = MultiEpgWindows.chunkContaining(nowSec)
        val stored = db.epgDao().eventsOverlapping(
            PROFILE,
            nestedFolder,
            chunk.startSec,
            chunk.endSec
        )
        assertEquals(listOf(channel), stored.map { it.serviceRef })
        assertTrue(stored.all { it.bouquetRef == nestedFolder })
        assertTrue(stored.none { it.serviceRef == nestedFolder })
    }

    @Test
    fun folderRefIsNotWrittenAsServiceRef() = runBlocking {
        seedFavouritesTab()
        val sync = syncReturning(
            channelEvent(nestedFolder).copy(title = "Folder programme")
        )
        UserBouquetEpgFill.ensureNowChunk(
            sync = sync,
            rosterDao = db.rosterDao(),
            profileId = PROFILE,
            containerRef = favourites.reference,
            tabRootRef = favourites.reference,
            excludedTabRefs = excluded,
            unixSec = nowSec
        )
        val chunk = MultiEpgWindows.chunkContaining(nowSec)
        val stored = db.epgDao().eventsOverlapping(
            PROFILE,
            favourites.reference,
            chunk.startSec,
            chunk.endSec
        )
        assertTrue(stored.none { it.serviceRef == nestedFolder })
        assertTrue(stored.isEmpty())
    }

    @Test
    fun providerAllIndexAndDefaultProviderDoNotWrite() = runBlocking {
        seedFavouritesTab()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tv = context.resources.getStringArray(R.array.servicerefstv)
        val sync = syncReturning(channelEvent(channel))
        val refs = listOf(tv[0], tv[1], tv[2])
        for (ref in refs) {
            val events = UserBouquetEpgFill.ensureNowChunk(
                sync = sync,
                rosterDao = db.rosterDao(),
                profileId = PROFILE,
                containerRef = ref,
                tabRootRef = ref,
                excludedTabRefs = excluded,
                unixSec = nowSec
            )
            assertTrue(events.isEmpty())
            val chunk = MultiEpgWindows.chunkContaining(nowSec)
            assertNull(db.epgDao().getChunk(PROFILE, ref, chunk.startSec))
        }
        val defaultProvider = UserBouquetEpgFill.ensureNowChunk(
            sync = sync,
            rosterDao = db.rosterDao(),
            profileId = PROFILE,
            containerRef = tv[1],
            tabRootRef = tv[1],
            excludedTabRefs = excluded,
            unixSec = nowSec,
            forceRefresh = true
        )
        assertTrue(defaultProvider.isEmpty())
        assertEquals(0, fetches.get())
    }

    private suspend fun seedFavouritesTab() {
        UserBouquetCache.replaceTabStrip(
            db.rosterDao(),
            PROFILE,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
    }

    private fun syncReturning(event: Event): MultiEpgSync = MultiEpgSync(
        dao = db.epgDao(),
        fetch = { _, _, _ ->
            fetches.incrementAndGet()
            listOf(event)
        },
        clockMs = { 1_000_000L }
    )

    private fun channelEvent(serviceRef: String): Event = Event(
        eventId = "1",
        title = "News",
        start = nowSec.toString(),
        duration = "1800",
        serviceReference = serviceRef,
        serviceName = "Das Erste HD"
    )

    companion object {
        private const val PROFILE = 7
    }
}
