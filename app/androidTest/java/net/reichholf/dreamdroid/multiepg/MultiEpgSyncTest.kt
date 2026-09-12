package net.reichholf.dreamdroid.multiepg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventParser
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MultiEpgSyncTest {
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
    fun parsesEpgmultiFixtureLikeEpgserviceShape() {
        val events = EventParser.parse(loadWebFixture("epgmulti.xml"))
        assertEquals(2, events.size)
        assertEquals("Das Erste HD", events[0].serviceName)
        assertEquals("ZDF HD", events[1].serviceName)
    }

    @Test
    fun chunkAlignmentIsTwentyFourHours() {
        val chunk = MultiEpgWindows.chunkContaining(1_893_456_000L)
        assertEquals(0L, chunk.startSec % MultiEpgWindows.CHUNK_SECONDS)
        assertEquals(chunk.startSec + MultiEpgWindows.CHUNK_SECONDS, chunk.endSec)
    }

    @Test
    fun ensureChunkFetchesOnceAndHitsTtl() = runBlocking {
        val fetches = AtomicInteger(0)
        val fixture = EventParser.parse(loadWebFixture("epgmulti.xml"))
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                fixture
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L,
        )
        val t0 = 1_893_456_000L
        val first = sync.ensureChunk(1, "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", t0)
        val second = sync.ensureChunk(1, "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", t0)
        assertEquals(2, first.size)
        assertEquals(2, second.size)
        assertEquals(1, fetches.get())
    }

    @Test
    fun singleFlightCoalescesConcurrentMisses() = runBlocking {
        val fetches = AtomicInteger(0)
        val fixture = EventParser.parse(loadWebFixture("epgmulti.xml"))
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                delay(150)
                fixture
            },
            clockMs = { 2_000_000L },
            ttlMs = 25L * 60L * 1000L,
        )
        val bouquet = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET"
        val t0 = 1_893_456_000L
        val results = listOf(
            async { sync.ensureChunk(1, bouquet, t0) },
            async { sync.ensureChunk(1, bouquet, t0) },
            async { sync.ensureChunk(1, bouquet, t0) },
        ).awaitAll()
        assertEquals(1, fetches.get())
        results.forEach { assertEquals(2, it.size) }
    }

    @Test
    fun staleTtlRefetches() = runBlocking {
        val fetches = AtomicInteger(0)
        var now = 1_000_000L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, end ->
                fetches.incrementAndGet()
                assertTrue(end > time)
                listOf(
                    Event(
                        eventId = fetches.get().toString(),
                        title = "T",
                        start = time.toString(),
                        duration = "60",
                        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                        serviceName = "TV",
                    ),
                )
            },
            clockMs = { now },
            ttlMs = 1_000L,
        )
        val bouquet = "bref"
        val t0 = MultiEpgWindows.CHUNK_SECONDS
        sync.ensureChunk(1, bouquet, t0)
        now += 2_000L
        sync.ensureChunk(1, bouquet, t0)
        assertEquals(2, fetches.get())
    }
}
