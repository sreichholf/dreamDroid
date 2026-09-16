package net.reichholf.dreamdroid.multiepg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiEpgCachePruneTest {
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
    fun pruneDeletesEventsAndChunksOlderThanTwoDays() = runBlocking {
        val day = MultiEpgWindows.CHUNK_SECONDS
        val now = 5L * day
        val cutoff = MultiEpgWindows.retentionCutoffSec(now)
        putEvent("old", start = 100L, duration = 60L)
        putEvent("recent", start = now - day, duration = 60L)
        putEvent("spanning", start = cutoff - 3_600L, duration = 7_200L)
        putChunk(windowStart = 0L, windowEnd = day)
        putChunk(windowStart = now - day, windowEnd = now)

        db.epgDao().pruneOlderThan(cutoff)

        assertEquals(setOf("recent", "spanning"), eventIds())
        assertNull(db.epgDao().getChunk(PROFILE, BOUQUET, 0L))
        assertNotNull(db.epgDao().getChunk(PROFILE, BOUQUET, now - day))
    }

    @Test
    fun ensureChunkPrunesStaleRowsWhenWriting() = runBlocking {
        val day = MultiEpgWindows.CHUNK_SECONDS
        val now = 5L * day
        putEvent("old", start = 100L, duration = 60L)
        putChunk(windowStart = 0L, windowEnd = day)
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                listOf(
                    Event(
                        eventId = "fresh",
                        title = "Fresh",
                        start = now.toString(),
                        duration = "60",
                        serviceReference = REF,
                        serviceName = "TV"
                    )
                )
            },
            clockMs = { now * 1000L }
        )

        sync.ensureChunk(PROFILE, BOUQUET, now + 10L)

        assertTrue(eventIds().contains("fresh"))
        assertTrue("old event must be pruned after a chunk write", "old" !in eventIds())
        assertNull(db.epgDao().getChunk(PROFILE, BOUQUET, 0L))
    }

    private suspend fun putEvent(id: String, start: Long, duration: Long) {
        db.epgDao().upsertEvents(
            listOf(
                EpgEventEntity(
                    profileId = PROFILE,
                    bouquetRef = BOUQUET,
                    serviceRef = REF,
                    eventId = id,
                    start = start,
                    duration = duration,
                    title = id,
                    description = "",
                    descriptionExtended = "",
                    serviceName = "TV"
                )
            )
        )
    }

    private suspend fun putChunk(windowStart: Long, windowEnd: Long) {
        db.epgDao().upsertChunk(
            EpgChunkMetaEntity(
                profileId = PROFILE,
                bouquetRef = BOUQUET,
                windowStart = windowStart,
                windowEnd = windowEnd,
                fetchedAtMs = 1L
            )
        )
    }

    private suspend fun eventIds(): Set<String> =
        db.epgDao().eventsOverlapping(PROFILE, BOUQUET, 0L, Long.MAX_VALUE)
            .map { it.eventId }
            .toSet()

    companion object {
        private const val PROFILE: Int = 1
        private const val BOUQUET: String = "bouquet-a"
        private const val REF: String = "1:0:1:1:1:1:0:0:0:0:"
    }
}
