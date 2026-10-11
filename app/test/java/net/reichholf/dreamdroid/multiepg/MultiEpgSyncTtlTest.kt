package net.reichholf.dreamdroid.multiepg

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.TestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MultiEpgSyncTtlTest {
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
    fun anHourLaterOnlyTheNearChunkIsFetchedAgain() = runBlocking {
        val day = MultiEpgWindows.CHUNK_SECONDS
        val nowSec = 1_000L * day + 3_600L
        var nowMs = nowSec * 1000L
        val fetchedStarts = mutableListOf<Long>()
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetchedStarts += time
                listOf(
                    Event(
                        eventId = "e-$time",
                        title = "News",
                        start = (time + 600L).toString(),
                        duration = "3600",
                        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                        serviceName = "TV"
                    )
                )
            },
            clockMs = { nowMs }
        )
        val near = nowSec
        val far = nowSec + 3 * day
        sync.ensureChunk(1, "bouquet-a", near)
        sync.ensureChunk(1, "bouquet-a", far)

        nowMs += 60L * 60L * 1000L
        sync.ensureChunk(1, "bouquet-a", near)
        sync.ensureChunk(1, "bouquet-a", far)

        val nearStart = MultiEpgWindows.chunkContaining(near).startSec
        val farStart = MultiEpgWindows.chunkContaining(far).startSec
        assertEquals(listOf(nearStart, farStart, nearStart), fetchedStarts)
    }
}
