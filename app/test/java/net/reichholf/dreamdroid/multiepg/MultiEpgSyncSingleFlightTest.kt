package net.reichholf.dreamdroid.multiepg

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.TestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
                listOf(
                    Event(
                        eventId = "e-$time",
                        title = "News",
                        start = time.toString(),
                        duration = "3600",
                        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                        serviceName = "TV"
                    )
                )
            },
            clockMs = { 1_000_000L }
        )

        val starter = launch { sync.ensureChunk(1, "bouquet-a", t0) }
        withTimeout(5_000L) { firstFetchStarted.await() }
        // Undispatched, so the waiter has joined the in-flight fetch before the starter is
        // cancelled.
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            sync.ensureChunk(1, "bouquet-a", t0, forceRefresh = true)
        }
        starter.cancel()

        val events = withTimeout(5_000L) { waiter.await() }
        assertEquals(listOf("News"), events.map { it.title })
        assertEquals(2, fetches.get())
    }
}
