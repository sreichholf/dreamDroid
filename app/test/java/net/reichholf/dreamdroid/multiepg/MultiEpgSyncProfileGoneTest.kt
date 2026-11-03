package net.reichholf.dreamdroid.multiepg

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.TestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A chunk whose profile was deleted is dropped, not stored as an empty chunk. */
class MultiEpgSyncProfileGoneTest {
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
    fun aChunkOfADeletedProfileIsNeverStored() = runBlocking {
        val nowSec = 1_000L * MultiEpgWindows.CHUNK_SECONDS + 3_600L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { profileId, _, _, _ -> throw MultiEpgProfileGoneException(profileId) },
            clockMs = { nowSec * 1000L }
        )

        assertTrue(sync.ensureChunk(DELETED_PROFILE, BOUQUET, nowSec).isEmpty())
        assertTrue(sync.ensureChunk(DELETED_PROFILE, BOUQUET, nowSec, persist = false).isEmpty())
        sync.warmChunk(DELETED_PROFILE, BOUQUET, nowSec)

        val chunkStart = MultiEpgWindows.chunkContaining(nowSec).startSec
        assertNull(db.epgDao().getChunk(DELETED_PROFILE, BOUQUET, chunkStart))
    }

    private companion object {
        const val DELETED_PROFILE = 7
        const val BOUQUET = "bouquet-a"
    }
}
