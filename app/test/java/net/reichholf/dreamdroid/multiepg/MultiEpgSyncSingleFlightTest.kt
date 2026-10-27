package net.reichholf.dreamdroid.multiepg

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.TestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MultiEpgSyncSingleFlightTest {
    private lateinit var db: AppDatabase

    @BeforeEach
    fun setUp() {
        db = AppDatabase.inMemory(TestContext())
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun waiterRefetchesWhenTheStartingCallerIsCancelled() = runBlocking {
        val fetches = AtomicInteger(0)
        val firstFetchStarted = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (fetches.incrementAndGet() == 1) {
                    firstFetchStarted.complete(Unit)
                    awaitCancellation()
                }
                listOf(news(time))
            },
            clockMs = { 1_000_000L }
        )

        val starter = launch { sync.ensureChunk(1, "bouquet-a", t0) }
        withTimeout(5_000L) { firstFetchStarted.await() }
        // Undispatched, and forceRefresh skips the Room read, so the waiter has joined the
        // in-flight fetch before the starter is cancelled.
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            sync.ensureChunk(1, "bouquet-a", t0, forceRefresh = true)
        }
        starter.cancel()

        val events = withTimeout(5_000L) { waiter.await() }
        assertEquals(listOf("News"), events.map { it.title })
        assertEquals(2, fetches.get())
    }

    @Test
    fun cancelledWaiterLeavesTheFetchToItsStarter() = runBlocking {
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                release.await()
                listOf(news(time))
            },
            clockMs = { 1_000_000L }
        )

        val starter = async { sync.ensureChunk(1, "bouquet-a", t0) }
        withTimeout(5_000L) { fetchStarted.await() }
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            sync.ensureChunk(1, "bouquet-a", t0, forceRefresh = true)
        }
        waiter.cancelAndJoin()
        release.complete(Unit)

        assertEquals(listOf("News"), withTimeout(5_000L) { starter.await() }.map { it.title })
        assertTrue(waiter.isCancelled)
        assertEquals(1, fetches.get())
    }

    @Test
    fun waiterGetsTheStartersFailure() = runBlocking {
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                release.await()
                error("epgmulti down")
            },
            clockMs = { 1_000_000L }
        )

        val starter = async { runCatching { sync.ensureChunk(1, "bouquet-a", t0) } }
        withTimeout(5_000L) { fetchStarted.await() }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { sync.ensureChunk(1, "bouquet-a", t0, forceRefresh = true) }
        }
        release.complete(Unit)

        val failure = withTimeout(5_000L) { waiter.await() }.exceptionOrNull()
        assertEquals("epgmulti down", failure?.message)
        assertEquals("epgmulti down", starter.await().exceptionOrNull()?.message)
        assertEquals(1, fetches.get())
    }

    @Test
    fun ensureChunkJoinsAConcurrentWarmChunk() = runBlocking {
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val chunkStart = MultiEpgWindows.chunkContaining(t0).startSec
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                release.await()
                listOf(news(time))
            },
            clockMs = { 1_000_000L }
        )

        val warm = async { sync.warmChunk(1, "bouquet-a", t0) }
        withTimeout(5_000L) { fetchStarted.await() }
        // Undispatched and forceRefresh skips the Room check, so the reader joins the
        // warm's flight before it is released.
        val reader = async(start = CoroutineStart.UNDISPATCHED) {
            sync.ensureChunk(1, "bouquet-a", t0, forceRefresh = true)
        }
        release.complete(Unit)

        assertEquals(listOf("News"), withTimeout(5_000L) { reader.await() }.map { it.title })
        warm.await()
        assertEquals(1, fetches.get())
        assertNotNull(db.epgDao().getChunk(1, "bouquet-a", chunkStart))
    }

    private fun news(time: Long): Event = Event(
        eventId = "e-$time",
        title = "News",
        start = time.toString(),
        duration = "3600",
        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
        serviceName = "TV"
    )
}
