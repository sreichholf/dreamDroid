package net.reichholf.dreamdroid.ui.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgBouquetCacheTest {
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
    fun offlineLoadPaintsRoomWithoutHostNotFound() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent())
        )
        val httpCalls = AtomicInteger(0)
        var emptyMessage: String? = null
        val session = EpgBouquetSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = EpgBouquetListState()
        session.refresh = ComposeRefreshState()
        session.bouquetRef = BOUQUET
        session.timeSec = NOW.toInt()
        session.profileId = PROFILE
        session.epgDao = dao
        session.onEmptyMessage = { emptyMessage = it }
        session.isSessionOnline = { false }
        session.loadEvents = { _, _ ->
            httpCalls.incrementAndGet()
            EventListLoadResult(false, emptyList(), "host_not_found")
        }
        session.loadAndApply(forceRefresh = false)
        assertEquals(0, httpCalls.get())
        assertEquals(listOf("News"), session.listState!!.items.map { it.title })
        assertNull(emptyMessage)
    }

    @Test
    fun httpFailFallsBackToRoomInsteadOfHostNotFound() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent())
        )
        var emptyMessage: String? = "stale"
        val session = EpgBouquetSession()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = EpgBouquetListState()
        session.refresh = ComposeRefreshState()
        session.bouquetRef = BOUQUET
        session.timeSec = NOW.toInt()
        session.profileId = PROFILE
        session.epgDao = dao
        session.onEmptyMessage = { emptyMessage = it }
        session.isSessionOnline = { true }
        session.loadEvents = { _, _ ->
            EventListLoadResult(false, emptyList(), "host_not_found")
        }
        session.loadAndApply(forceRefresh = true)
        assertEquals(listOf("News"), session.listState!!.items.map { it.title })
        assertNull(emptyMessage)
    }

    private fun sampleEvent(): EpgEventEntity = EpgEventEntity(
        profileId = PROFILE,
        bouquetRef = BOUQUET,
        serviceRef = CHANNEL,
        eventId = "1",
        start = NOW,
        duration = 3600,
        title = "News",
        description = "",
        descriptionExtended = "",
        serviceName = "Das Erste HD"
    )

    companion object {
        private const val PROFILE = 7
        private const val NOW = 1_893_456_000L
        private const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        private const val CHANNEL = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
    }
}
