package net.reichholf.dreamdroid.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UseDrivenCacheTest {
    private lateinit var db: AppDatabase
    private lateinit var excluded: Set<String>

    private val favourites = Service(
        "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
        "Favourites (TV)"
    )
    private val channel = ServiceNowNext(
        serviceReference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
        serviceName = "Das Erste HD"
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        excluded = UserBouquetCache.excludedHubTabRefs(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun clearForProfileDropsOnlyThatProfile() = runBlocking {
        seedProfile(PROFILE)
        seedProfile(OTHER)

        UseDrivenCache.clearForProfile(db, PROFILE)

        assertFalse(
            hasUseDrivenCache(
                db.rosterDao().getTabStripRefs(PROFILE),
                db.movieDao().locationMetaCount(PROFILE) > 0,
                db.timerDao().snapshotCount(PROFILE) > 0
            )
        )
        assertNull(
            UserBouquetCache.loadRosterNowNext(db.rosterDao(), PROFILE, favourites.reference)
        )
        assertNull(
            db.epgDao().getChunk(PROFILE, favourites.reference, WINDOW_START)
        )
        assertTrue(
            db.epgDao().eventsOverlapping(
                PROFILE,
                favourites.reference,
                WINDOW_START,
                WINDOW_END
            ).isEmpty()
        )
        assertNull(TimerSnapshotStore.load(db.timerDao(), PROFILE))
        assertNull(MovieSnapshotStore.loadLocations(db.movieDao(), PROFILE))
        assertNull(MovieSnapshotStore.loadMovies(db.movieDao(), PROFILE, HDD))

        assertTrue(
            hasUseDrivenCache(
                db.rosterDao().getTabStripRefs(OTHER),
                db.movieDao().locationMetaCount(OTHER) > 0,
                db.timerDao().snapshotCount(OTHER) > 0
            )
        )
        assertEquals(
            listOf(channel.serviceName),
            UserBouquetCache.loadRosterNowNext(
                db.rosterDao(),
                OTHER,
                favourites.reference
            )?.map { it.serviceName }
        )
        assertNotNull(db.epgDao().getChunk(OTHER, favourites.reference, WINDOW_START))
        assertEquals(
            listOf("News"),
            db.epgDao().eventsOverlapping(
                OTHER,
                favourites.reference,
                WINDOW_START,
                WINDOW_END
            ).map { it.title }
        )
        assertEquals(
            listOf("News"),
            TimerSnapshotStore.load(db.timerDao(), OTHER)?.map { it.name }
        )
        assertEquals(listOf(HDD), MovieSnapshotStore.loadLocations(db.movieDao(), OTHER))
        assertEquals(
            listOf("News"),
            MovieSnapshotStore.loadMovies(db.movieDao(), OTHER, HDD)?.map { it.title }
        )
    }

    @Test
    fun clearAllDropsEveryProfile() = runBlocking {
        seedProfile(PROFILE)
        seedProfile(OTHER)

        UseDrivenCache.clearAll(db)

        assertFalse(
            hasUseDrivenCache(
                db.rosterDao().getTabStripRefs(PROFILE),
                db.movieDao().locationMetaCount(PROFILE) > 0,
                db.timerDao().snapshotCount(PROFILE) > 0
            )
        )
        assertFalse(
            hasUseDrivenCache(
                db.rosterDao().getTabStripRefs(OTHER),
                db.movieDao().locationMetaCount(OTHER) > 0,
                db.timerDao().snapshotCount(OTHER) > 0
            )
        )
        assertNull(
            UserBouquetCache.loadRosterNowNext(db.rosterDao(), OTHER, favourites.reference)
        )
        assertNull(db.epgDao().getChunk(OTHER, favourites.reference, WINDOW_START))
        assertNull(TimerSnapshotStore.load(db.timerDao(), OTHER))
        assertNull(MovieSnapshotStore.loadLocations(db.movieDao(), OTHER))
        assertNull(MovieSnapshotStore.loadMovies(db.movieDao(), OTHER, HDD))
    }

    private suspend fun seedProfile(profileId: Int) {
        UserBouquetCache.replaceTabStrip(
            db.rosterDao(),
            profileId,
            UserBouquetCache.KIND_TV,
            listOf(favourites),
            excluded
        )
        UserBouquetCache.persistRosterIfCacheable(
            db.rosterDao(),
            profileId,
            favourites.reference,
            favourites.reference,
            listOf(channel),
            excluded
        )
        db.epgDao().upsertChunk(
            EpgChunkMetaEntity(
                profileId = profileId,
                bouquetRef = favourites.reference,
                windowStart = WINDOW_START,
                windowEnd = WINDOW_END,
                fetchedAtMs = 1L
            )
        )
        db.epgDao().upsertEvents(
            listOf(
                EpgEventEntity(
                    profileId = profileId,
                    bouquetRef = favourites.reference,
                    serviceRef = channel.serviceReference,
                    eventId = "10",
                    start = WINDOW_START + 60,
                    duration = 1_800L,
                    title = "News",
                    description = "",
                    descriptionExtended = "",
                    serviceName = channel.serviceName
                )
            )
        )
        TimerSnapshotStore.replace(
            db.timerDao(),
            profileId,
            listOf(
                Timer(
                    reference = channel.serviceReference,
                    serviceName = channel.serviceName,
                    eit = "10",
                    name = "News",
                    begin = "1476644933",
                    end = "1476649083"
                )
            )
        )
        MovieSnapshotStore.replaceLocations(db.movieDao(), profileId, listOf(HDD))
        MovieSnapshotStore.replaceMovies(
            db.movieDao(),
            profileId,
            HDD,
            listOf(
                Movie(
                    reference = "1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/news.ts",
                    title = "News",
                    fileName = "/media/hdd/movie/news.ts"
                )
            )
        )
    }

    companion object {
        private const val PROFILE = 7
        private const val OTHER = 8
        private const val HDD = "/media/hdd/movie"
        private const val WINDOW_START = 1_893_456_000L
        private const val WINDOW_END = WINDOW_START + 86_400L
    }
}
