package net.reichholf.dreamdroid.multiepg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class MultiEpgSessionTest {
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
    fun paintsPeekThenRefreshesStaleChunk() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fetches = AtomicInteger(0)
        var now = 1_000_000L
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                val n = fetches.incrementAndGet()
                if (n > 1) {
                    gate.await()
                }
                listOf(programme(id = "e1", title = "T$n", start = time))
            },
            clockMs = { now },
            ttlMs = 1_000L,
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { session.channels.isNotEmpty() }
        assertEquals("T1", firstTitle(session))
        assertTrue(session.syncing)
        gate.complete(Unit)
        session.awaitIdle()
        assertEquals("T2", firstTitle(session))
        assertFalse(session.syncing)
        assertEquals(null, session.errorMessage)
    }

    @Test
    fun keepsStaleGridWhenRefreshFails() = runBlocking {
        val fetches = AtomicInteger(0)
        var now = 1_000_000L
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (fetches.incrementAndGet() > 1) {
                    error("box down")
                }
                listOf(programme(id = "1", title = "Old", start = time))
            },
            clockMs = { now },
            ttlMs = 1_000L,
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("Old", firstTitle(session))
        assertEquals("box down", session.errorMessage)
        assertFalse(session.syncing)
    }

    @Test
    fun pullRefreshForcesFetchWhileFresh() = runBlocking {
        val visibleFetches = AtomicInteger(0)
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val chunk = MultiEpgWindows.chunkContaining(t0)
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time == chunk.startSec) {
                    visibleFetches.incrementAndGet()
                }
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L,
        )
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        val afterLoad = visibleFetches.get()
        assertTrue(afterLoad >= 1)
        session.load(t0, forceRefresh = true, isPull = true)
        assertTrue(session.pullRefreshing)
        session.awaitIdle()
        assertEquals(afterLoad + 1, visibleFetches.get())
        assertFalse(session.pullRefreshing)
    }

    @Test
    fun freshPeekPrefetchKeepsToolbarSyncing() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val visibleStart = MultiEpgWindows.chunkContaining(t0).startSec
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time != visibleStart) {
                    gate.await()
                }
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L,
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { session.channels.isNotEmpty() }
        assertEquals("T", firstTitle(session))
        assertTrue(session.syncing)
        gate.complete(Unit)
        session.awaitIdle()
        assertFalse(session.syncing)
    }

    @Test
    fun replaceAndLoadDropsPreviousBouquetImmediately() = runBlocking {
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { bouquet, time, _ ->
                val title = if (bouquet == "bouquet-a") "A" else "B"
                listOf(programme(id = "$bouquet-$time", title = title, start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L,
        )
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("A", firstTitle(session))
        session.replaceAndLoad("bouquet-b", t0)
        assertTrue(session.channels.isEmpty())
        session.awaitIdle()
        assertEquals("B", firstTitle(session))
    }

    private fun firstTitle(session: MultiEpgSession): String {
        return session.channels.first().bars.first().event.title
    }

    private fun programme(id: String, title: String, start: Long): Event {
        return Event(
            eventId = id,
            title = title,
            start = start.toString(),
            duration = "3600",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "TV",
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val startMs = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - startMs > timeoutMs) {
                error("timed out waiting for session condition")
            }
            delay(10)
        }
    }
}
