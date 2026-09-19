package net.reichholf.dreamdroid.ui.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListEpgCacheTest {
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
    fun bouquetEventsComeFromRoomChunk() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent(title = "News"))
        )
        val loaded = ListEpgCache.loadBouquetEvents(dao, PROFILE, BOUQUET, NOW)
        assertEquals(listOf("News"), loaded?.map { it.title })
        assertNull(ListEpgCache.loadBouquetEvents(dao, PROFILE, OTHER_BOUQUET, NOW))
        assertNull(ListEpgCache.loadBouquetEvents(dao, OTHER_PROFILE, BOUQUET, NOW))
    }

    @Test
    fun writtenEmptyBouquetIsEmptyListNotMissing() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            emptyList()
        )
        val loaded = ListEpgCache.loadBouquetEvents(dao, PROFILE, BOUQUET, NOW)
        assertEquals(emptyList<String>(), loaded?.map { it.title })
    }

    @Test
    fun serviceEventsComeFromRoomWithoutBouquetHttp() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent(title = "News"), sampleEvent(title = "Other", service = OTHER))
        )
        val loaded = ListEpgCache.loadServiceEvents(dao, PROFILE, CHANNEL, NOW)
        assertEquals(listOf("News"), loaded?.map { it.title })
        assertNull(ListEpgCache.loadServiceEvents(dao, PROFILE, "1:0:1:missing", NOW))
    }

    @Test
    fun bouquetEventsAreOneProgrammePerChannelAtFromSec() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(
                sampleEvent(title = "News", start = NOW, duration = 3600),
                sampleEvent(title = "Talk", start = NOW + 3600, duration = 3600),
                sampleEvent(
                    title = "Match",
                    service = OTHER,
                    start = NOW - 600,
                    duration = 7200
                ),
                sampleEvent(
                    title = "Studio",
                    service = OTHER,
                    start = NOW + 6600,
                    duration = 1800
                )
            )
        )
        val loaded = ListEpgCache.loadBouquetEvents(dao, PROFILE, BOUQUET, NOW)
        assertEquals(listOf("News", "Match"), loaded?.map { it.title })
        val later = ListEpgCache.loadBouquetEvents(dao, PROFILE, BOUQUET, NOW + 4000)
        assertEquals(listOf("Talk", "Match"), later?.map { it.title })
    }

    private fun sampleEvent(
        title: String,
        service: String = CHANNEL,
        start: Long = NOW,
        duration: Long = 3600
    ): EpgEventEntity = EpgEventEntity(
        profileId = PROFILE,
        bouquetRef = BOUQUET,
        serviceRef = service,
        eventId = title,
        start = start,
        duration = duration,
        title = title,
        description = "",
        descriptionExtended = "",
        serviceName = "Das Erste HD"
    )

    companion object {
        private const val PROFILE = 7
        private const val OTHER_PROFILE = 8
        private const val NOW = 1_893_456_000L
        private const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        private const val OTHER_BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet"
        private const val CHANNEL = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
        private const val OTHER = "1:0:1:6DCB:44C:1:C00000:0:0:0:"
    }
}
