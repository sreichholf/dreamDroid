package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
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
        val viewModel = seededViewModel()
        viewModel.loadHooks = EpgBouquetLoadHooks(
            profileId = { PROFILE },
            epgDao = { dao },
            shouldSkipReceiverHttp = { hasCache ->
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline
                ).shouldSkipReceiverHttp(hasCache)
            },
            loadEvents = { _, _ ->
                httpCalls.incrementAndGet()
                EventListLoadResult(false, emptyList(), "host_not_found")
            }
        )
        viewModel.reload()
        viewModel.awaitLoad()
        assertEquals(0, httpCalls.get())
        assertEquals(listOf("News"), viewModel.listState.items.map { it.title })
        assertNull(viewModel.emptyMessage)
    }

    @Test
    fun offlineLoadKeepsOneProgrammePerChannelAtSelectedTime() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(
                sampleEvent(title = "News", start = NOW, duration = 3600),
                sampleEvent(title = "Talk", start = NOW + 3600, duration = 3600)
            )
        )
        val viewModel = seededViewModel()
        viewModel.loadHooks = EpgBouquetLoadHooks(
            profileId = { PROFILE },
            epgDao = { dao },
            shouldSkipReceiverHttp = { hasCache ->
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline
                ).shouldSkipReceiverHttp(hasCache)
            },
            loadEvents = { _, _ -> error("http") }
        )
        viewModel.reload()
        viewModel.awaitLoad()
        assertEquals(listOf("News"), viewModel.listState.items.map { it.title })
    }

    @Test
    fun checkingWithCachePaintsThenHitsHttp() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent())
        )
        val httpCalls = AtomicInteger(0)
        var paintedBeforeHttp: List<String> = emptyList()
        val viewModel = seededViewModel()
        viewModel.loadHooks = EpgBouquetLoadHooks(
            profileId = { PROFILE },
            epgDao = { dao },
            shouldSkipReceiverHttp = { hasCache ->
                ConnectionStatus(checking = true).shouldSkipReceiverHttp(hasCache)
            },
            loadEvents = { _, _ ->
                paintedBeforeHttp = viewModel.listState.items.map { it.title }
                httpCalls.incrementAndGet()
                EventListLoadResult(true, listOf(Event(title = "Live News")), null)
            }
        )
        viewModel.reload()
        viewModel.awaitLoad()
        assertEquals(1, httpCalls.get())
        assertEquals(listOf("News"), paintedBeforeHttp)
        assertEquals(listOf("Live News"), viewModel.listState.items.map { it.title })
        assertNull(viewModel.emptyMessage)
    }

    @Test
    fun httpFailFallsBackToRoomInsteadOfHostNotFound() = runBlocking {
        val dao = db.epgDao()
        val chunk = MultiEpgWindows.chunkContaining(NOW)
        dao.replaceChunk(
            EpgChunkMetaEntity(PROFILE, BOUQUET, chunk.startSec, chunk.endSec, 1L),
            listOf(sampleEvent())
        )
        val viewModel = seededViewModel()
        viewModel.loadHooks = EpgBouquetLoadHooks(
            profileId = { PROFILE },
            epgDao = { dao },
            shouldSkipReceiverHttp = { false },
            loadEvents = { _, _ ->
                EventListLoadResult(false, emptyList(), "host_not_found")
            }
        )
        viewModel.reload(forceRefresh = true)
        viewModel.awaitLoad()
        assertEquals(listOf("News"), viewModel.listState.items.map { it.title })
        assertNull(viewModel.emptyMessage)
    }

    private fun seededViewModel(): EpgBouquetViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Application
        val viewModel = EpgBouquetViewModel(app, SavedStateHandle())
        viewModel.ensureEpoch(
            epoch = 1,
            leafRef = BOUQUET,
            leafName = "Favourites",
            leafTimeSec = NOW,
            nowSec = NOW.toInt()
        )
        return viewModel
    }

    private suspend fun EpgBouquetViewModel.awaitLoad() {
        val job = checkNotNull(loadJob)
        var failure: Throwable? = null
        job.invokeOnCompletion { cause -> failure = cause }
        withTimeout(10_000) { job.join() }
        failure?.let { throw it }
    }

    private fun sampleEvent(
        title: String = "News",
        start: Long = NOW,
        duration: Long = 3600
    ): EpgEventEntity = EpgEventEntity(
        profileId = PROFILE,
        bouquetRef = BOUQUET,
        serviceRef = CHANNEL,
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
        private const val NOW = 1_893_456_000L
        private const val BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        private const val CHANNEL = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
    }
}
