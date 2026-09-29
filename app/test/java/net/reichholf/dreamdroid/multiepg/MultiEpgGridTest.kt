package net.reichholf.dreamdroid.multiepg

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.EnigmaFailureException
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.testutil.TestContext
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MultiEpgGridTest {
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
    fun paintsPeekThenRefreshesStaleChunk() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val focusedFetches = AtomicInteger(0)
        var now = 1_000_000L
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val focusedStart = MultiEpgWindows.chunkContaining(t0).startSec
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time == focusedStart) {
                    val n = focusedFetches.incrementAndGet()
                    if (n > 1) {
                        gate.await()
                    }
                    listOf(programme(id = "e-$time", title = "T$n", start = time))
                } else {
                    listOf(programme(id = "e-$time", title = "P", start = time))
                }
            },
            clockMs = { now },
            ttlMs = 1_000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitState { titleOnFocusedChunkOrNull(grid, t0) != null }
        assertEquals("T1", titleOnFocusedChunk(grid, t0))
        assertTrue(grid.state.value.syncing)
        gate.complete(Unit)
        grid.awaitIdle()
        assertEquals("T2", titleOnFocusedChunk(grid, t0))
        assertFalse(grid.state.value.syncing)
        assertEquals(null, grid.state.value.errorMessage)
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
                listOf(programme(id = "e-$time", title = "Old", start = time))
            },
            clockMs = { now },
            ttlMs = 1_000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        assertEquals("Old", titleOnFocusedChunk(grid, t0))
        assertEquals(UiText.Raw("box down"), grid.state.value.errorMessage)
        assertFalse(grid.state.value.syncing)
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
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        val afterLoad = visibleFetches.get()
        assertTrue(afterLoad >= 1)
        grid.load(t0, forceRefresh = true, isPull = true)
        assertTrue(grid.state.value.pullRefreshing)
        grid.awaitIdle()
        assertEquals(afterLoad + 1, visibleFetches.get())
        assertFalse(grid.state.value.pullRefreshing)
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
            ttlMs = 25L * 60L * 1000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitState { grid.state.value.channels.isNotEmpty() }
        assertEquals("T", titleOnFocusedChunk(grid, t0))
        assertTrue(grid.state.value.syncing)
        gate.complete(Unit)
        grid.awaitIdle()
        assertFalse(grid.state.value.syncing)
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
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        assertEquals("A", titleOnFocusedChunk(grid, t0))
        grid.replaceAndLoad("bouquet-b", t0)
        assertTrue(grid.state.value.channels.isEmpty())
        grid.awaitIdle()
        assertEquals("B", titleOnFocusedChunk(grid, t0))
    }

    @Test
    fun lateInUtcDayOriginIsNowAndPrefetchesTomorrow() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val lateNow = chunk.endSec - 600L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", lateNow)
        grid.awaitIdle()
        assertEquals(lateNow, grid.state.value.timelineStartSec)
        assertEquals(lateNow, grid.state.value.originFloorSec)
        assertTrue(grid.state.value.timelineEndSec > chunk.endSec)
        assertEquals(chunk.startSec, grid.loadedWindowStarts.minOrNull())
        assertFalse(
            grid.loadedWindowStarts.contains(chunk.startSec - MultiEpgWindows.CHUNK_SECONDS)
        )
    }

    @Test
    fun visibleWindowDropsOffscreenPastAndRestoresFromCache() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.endSec - 600L
        val day2 = chunk.startSec + 2L * MultiEpgWindows.CHUNK_SECONDS
        val fetches = ArrayList<Long>()
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.add(time)
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        assertTrue(grid.loadedWindowStarts.contains(chunk.startSec))

        grid.onVisibleWindow(now, now + 7200L)
        grid.awaitIdle()
        assertTrue(
            grid.loadedWindowStarts.contains(chunk.startSec),
            "panning inside the padded window must keep today"
        )

        grid.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        grid.awaitIdle()
        assertFalse(grid.loadedWindowStarts.contains(chunk.startSec))
        assertTrue(grid.state.value.timelineStartSec >= chunk.endSec)

        val fetchesBeforeRestore = fetches.size
        grid.onVisibleWindow(now, now + 7200L)
        grid.awaitIdle()
        assertTrue(grid.loadedWindowStarts.contains(chunk.startSec))
        assertEquals(now, grid.state.value.timelineStartSec)
        assertEquals(
            fetchesBeforeRestore,
            fetches.size,
            "restoring today should peek Room, not refetch the box"
        )
    }

    @Test
    fun slidingWindowNeverLoadsYesterday() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.startSec + 3_600L
        val yesterday = chunk.startSec - MultiEpgWindows.CHUNK_SECONDS
        val fetches = ArrayList<Long>()
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.add(time)
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        grid.onVisibleWindow(now, now + 7200L)
        grid.awaitIdle()
        grid.focusAt(now - MultiEpgWindows.CHUNK_SECONDS)
        grid.awaitIdle()
        assertFalse(fetches.contains(yesterday))
        assertFalse(grid.loadedWindowStarts.contains(yesterday))
        assertEquals(now, grid.state.value.originFloorSec)
        assertEquals(now, grid.state.value.timelineStartSec)
    }

    @Test
    fun timelineStartIsEarliestProgrammeCrossingNow() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.startSec + 10_800L
        val earliest = now - 7_200L
        val later = now - 600L
        val yesterday = chunk.startSec - MultiEpgWindows.CHUNK_SECONDS
        val fetches = ArrayList<Long>()
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.add(time)
                if (time == chunk.startSec) {
                    listOf(
                        programme(
                            id = "early",
                            title = "Early",
                            start = earliest,
                            duration = "14400"
                        ),
                        programme(
                            id = "later",
                            title = "Later",
                            start = later,
                            duration = "3600"
                        )
                    )
                } else {
                    listOf(programme(id = time.toString(), title = "T", start = time))
                }
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        assertEquals(earliest, grid.state.value.timelineStartSec)
        assertEquals(now, grid.state.value.originFloorSec)
        assertFalse(fetches.contains(yesterday))
        assertFalse(grid.loadedWindowStarts.contains(yesterday))
    }

    @Test
    fun slidingAttachSetsSyncingWhileFetching() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.endSec - 600L
        val day2 = chunk.startSec + 2L * MultiEpgWindows.CHUNK_SECONDS
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time == day2) {
                    gate.await()
                }
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        assertFalse(grid.state.value.syncing)

        grid.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        grid.awaitState { grid.state.value.syncing }
        assertTrue(grid.state.value.channels.isNotEmpty())
        gate.complete(Unit)
        grid.awaitIdle()
        assertFalse(grid.state.value.syncing)
        assertTrue(grid.loadedWindowStarts.contains(day2))
        assertTrue(grid.state.value.channels.isNotEmpty())
    }

    @Test
    fun paintsGridBeforeTimerClocksAndIgnoresTimerFetchErrors() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                listOf(programme(id = "e-$time", title = "News", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            fetchTimers = {
                gate.await()
                error("timerlist down")
            }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitState { grid.state.value.channels.isNotEmpty() }
        assertTrue(grid.state.value.timerClocks.isEmpty())
        gate.complete(Unit)
        grid.awaitIdle()
        assertTrue(grid.state.value.channels.isNotEmpty())
        assertTrue(grid.state.value.timerClocks.isEmpty())
        assertEquals(null, grid.state.value.errorMessage)
    }

    @Test
    fun overlaysRecordClockFromTimerList() = runBlocking {
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                listOf(programme(id = "e-$time", title = "News", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            fetchTimers = {
                listOf(
                    Timer(
                        reference = "1:0:1:1:1:1:0:0:0:0:",
                        begin = t0.toString(),
                        end = (t0 + 3600L).toString(),
                        justPlay = "0",
                        disabled = "0",
                        repeated = "0"
                    )
                )
            }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        grid.awaitState(dump = { gridDump(grid, t0) + " clocks=${grid.state.value.timerClocks}" }) {
            grid.state.value.timerClocks.isNotEmpty()
        }
        assertEquals(MultiEpgTimerClock.Record, grid.state.value.timerClocks.values.single())
    }

    @Test
    fun bouquetRosterKeepsRowsWhenWindowHasNoEvents() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.endSec - 600L
        val day2 = chunk.startSec + 2L * MultiEpgWindows.CHUNK_SECONDS
        val withEpg = "1:0:1:1:1:1:0:0:0:0:"
        val withoutEpg = "1:0:1:2:1:1:0:0:0:0:"
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                val events = ArrayList<Event>(2)
                events.add(
                    programme(
                        id = "$time-a",
                        title = "T",
                        start = time,
                        serviceReference = withEpg,
                        serviceName = "Das Erste"
                    )
                )
                if (time == chunk.startSec) {
                    events.add(
                        programme(
                            id = "$time-b",
                            title = "B",
                            start = time,
                            serviceReference = withoutEpg,
                            serviceName = "ZDF"
                        )
                    )
                }
                events
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            loadBouquetServices = {
                listOf(
                    Service(withEpg, "Das Erste"),
                    Service(withoutEpg, "ZDF")
                )
            }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        assertEquals(2, grid.state.value.channels.size)
        assertEquals("Das Erste", grid.state.value.channels[0].serviceName)
        assertEquals("ZDF", grid.state.value.channels[1].serviceName)
        assertTrue(grid.state.value.channels[1].bars.isNotEmpty())

        grid.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        grid.awaitIdle()
        assertEquals(2, grid.state.value.channels.size)
        assertEquals("ZDF", grid.state.value.channels[1].serviceName)
        assertTrue(
            grid.state.value.channels[1].bars.isEmpty(),
            "ZDF stays in the grid after today is dropped"
        )
        assertTrue(grid.state.value.channels[0].bars.isNotEmpty())
    }

    @Test
    fun spanningProgrammeIsOneBarAcrossAdjacentChunks() = runBlocking {
        val chunk0 = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val spanStart = chunk0.endSec - 1800L
        val spanning = programme(
            id = "span",
            title = "Overnight",
            start = spanStart,
            duration = "7200"
        )
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time == chunk0.startSec) {
                    listOf(
                        spanning,
                        programme(id = "a", title = "A", start = time)
                    )
                } else {
                    listOf(
                        spanning,
                        programme(id = "b", title = "B", start = time)
                    )
                }
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val origin = chunk0.endSec - 600L
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", origin)
        grid.awaitIdle()
        grid.onVisibleWindow(origin, origin + 4L * 3600L)
        grid.awaitIdle()
        val spanBars = grid.state.value.channels.single().bars.filter { it.event.eventId == "span" }
        assertEquals(1, spanBars.size)
        assertEquals("Overnight", spanBars.single().event.title)
    }

    @Test
    fun failedGetservicesKeepsLastGoodRoster() = runBlocking {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.endSec - 600L
        val withEpg = "1:0:1:1:1:1:0:0:0:0:"
        val withoutEpg = "1:0:1:2:1:1:0:0:0:0:"
        val bouquetCalls = AtomicInteger(0)
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                listOf(
                    programme(
                        id = "$time-a",
                        title = "T",
                        start = time,
                        serviceReference = withEpg,
                        serviceName = "Das Erste"
                    )
                )
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            loadBouquetServices = {
                if (bouquetCalls.incrementAndGet() > 1) {
                    error("getservices down")
                }
                listOf(
                    Service(withEpg, "Das Erste"),
                    Service(withoutEpg, "ZDF")
                )
            }
        )
        grid.replaceAndLoad("bouquet-a", now)
        grid.awaitIdle()
        assertEquals(2, grid.state.value.channels.size)
        assertEquals("ZDF", grid.state.value.channels[1].serviceName)
        assertEquals(null, grid.state.value.errorMessage)

        grid.load(now, forceRefresh = true, isPull = true)
        grid.awaitIdle()
        assertEquals(2, grid.state.value.channels.size)
        assertEquals("ZDF", grid.state.value.channels[1].serviceName)
        assertEquals(UiText.Raw("getservices down"), grid.state.value.errorMessage)
    }

    @Test
    fun offlinePeekPaintsWithoutWaitingForHttp() = runBlocking {
        val hang = CompletableDeferred<Unit>()
        val fetches = AtomicInteger(0)
        var now = 1_000_000L
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                val n = fetches.incrementAndGet()
                if (n > 1) {
                    hang.await()
                    error("epgmulti down")
                }
                listOf(programme(id = "e-$time", title = "Cached", start = time))
            },
            clockMs = { now },
            ttlMs = 1_000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val bouquetCalls = AtomicInteger(0)
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            persistBouquet = { false },
            shouldSkipReceiverHttp = { hasCache -> hasCache },
            loadBouquetServices = {
                bouquetCalls.incrementAndGet()
                hang.await()
                error("getservices down")
            }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitState { titleOnFocusedChunkOrNull(grid, t0) != null }
        assertEquals("Cached", titleOnFocusedChunk(grid, t0))
        assertEquals(1, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, grid.state.value.errorMessage)
        grid.cancel()
        hang.cancel()
    }

    @Test
    fun offlineEventsWithoutChunkMetaPaintWithoutHttp() = runBlocking {
        val hang = CompletableDeferred<Unit>()
        val fetches = AtomicInteger(0)
        val bouquetCalls = AtomicInteger(0)
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val bouquet = "bouquet-a"
        db.epgDao().upsertEvents(
            listOf(
                programme(id = "e-room", title = "HubFill", start = t0)
            ).toEpgEventEntities(1, bouquet)
        )
        assertNull(
            db.epgDao().getChunk(1, bouquet, MultiEpgWindows.chunkContaining(t0).startSec)
        )
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            },
            clockMs = { 1_000_000L },
            ttlMs = 1_000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            persistBouquet = { false },
            isSessionOffline = { true },
            loadBouquetServices = {
                bouquetCalls.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            }
        )
        grid.replaceAndLoad(bouquet, t0)
        grid.awaitState { titleOnFocusedChunkOrNull(grid, t0) != null }
        assertEquals("HubFill", titleOnFocusedChunk(grid, t0))
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, grid.state.value.errorMessage)
        grid.cancel()
        hang.cancel()
    }

    @Test
    fun offlineSkipDoesNotWaitOnHangingGetservices() = runBlocking {
        val hang = CompletableDeferred<Unit>()
        val fetches = AtomicInteger(0)
        val bouquetCalls = AtomicInteger(0)
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            },
            clockMs = { 1_000_000L },
            ttlMs = 1_000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            persistBouquet = { false },
            isSessionOffline = { true },
            loadBouquetServices = {
                bouquetCalls.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, grid.state.value.errorMessage)
        grid.cancel()
        hang.cancel()
    }

    @Test
    fun checkingOverlapPeekStillHitsHttp() = runBlocking {
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val bouquet = "bouquet-a"
        db.epgDao().upsertEvents(
            listOf(
                programme(id = "e-room", title = "HubFill", start = t0)
            ).toEpgEventEntities(1, bouquet)
        )
        val fetches = AtomicInteger(0)
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                fetches.incrementAndGet()
                listOf(programme(id = "e-$time", title = "Live", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 1_000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad(bouquet, t0)
        grid.awaitState { titleOnFocusedChunkOrNull(grid, t0) != null }
        grid.awaitIdle()
        assertTrue(fetches.get() >= 1)
        assertEquals("Live", titleOnFocusedChunk(grid, t0))
    }

    @Test
    fun offlinePullRefreshUnreachableKeepsGridWithoutHostError() = runBlocking {
        val fetches = AtomicInteger(0)
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (fetches.incrementAndGet() > 1) {
                    throw EnigmaFailureException(
                        EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                    )
                }
                listOf(programme(id = "e-$time", title = "Cached", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            persistBouquet = { false },
            isSessionOffline = { true },
            loadBouquetServices = {
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        assertEquals("Cached", titleOnFocusedChunk(grid, t0))
        assertEquals(null, grid.state.value.errorMessage)

        grid.load(t0, forceRefresh = true, isPull = true)
        grid.awaitIdle()
        assertEquals("Cached", titleOnFocusedChunk(grid, t0))
        assertEquals(null, grid.state.value.errorMessage)
        assertTrue(fetches.get() >= 2)
    }

    @Test
    fun offlineVisibleWindowDoesNotHitHttp() = runBlocking {
        val hang = CompletableDeferred<Unit>()
        val fetches = AtomicInteger(0)
        val bouquetCalls = AtomicInteger(0)
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val bouquet = "bouquet-a"
        db.epgDao().upsertEvents(
            listOf(
                programme(id = "e-room", title = "HubFill", start = t0)
            ).toEpgEventEntities(1, bouquet)
        )
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, _, _ ->
                fetches.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            },
            clockMs = { 1_000_000L },
            ttlMs = 1_000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 },
            persistBouquet = { false },
            isSessionOffline = { true },
            loadBouquetServices = {
                bouquetCalls.incrementAndGet()
                hang.await()
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            }
        )
        grid.replaceAndLoad(bouquet, t0)
        grid.awaitState { titleOnFocusedChunkOrNull(grid, t0) != null }
        grid.awaitIdle()
        assertEquals("HubFill", titleOnFocusedChunk(grid, t0))
        val day2 = MultiEpgWindows.chunkContaining(t0).startSec +
            2L * MultiEpgWindows.CHUNK_SECONDS
        grid.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        grid.awaitIdle()
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, grid.state.value.errorMessage)
        grid.cancel()
        hang.cancel()
    }

    @Test
    fun prefetchFailureKeepsGridAndSurfacesError() = runBlocking {
        val t0 = MultiEpgWindows.CHUNK_SECONDS + 10L
        val visibleStart = MultiEpgWindows.chunkContaining(t0).startSec
        val sync = MultiEpgSync(
            dao = db.epgDao(),
            fetch = { _, time, _ ->
                if (time != visibleStart) {
                    error("prefetch down")
                }
                listOf(programme(id = time.toString(), title = "T", start = time))
            },
            clockMs = { 1_000_000L },
            ttlMs = 25L * 60L * 1000L
        )
        val grid = MultiEpgGrid(
            sync = sync,
            scope = this,
            profileId = { 1 }
        )
        grid.replaceAndLoad("bouquet-a", t0)
        grid.awaitIdle()
        assertEquals("T", titleOnFocusedChunk(grid, t0))
        assertTrue(grid.state.value.channels.isNotEmpty())
        assertEquals(UiText.Raw("prefetch down"), grid.state.value.errorMessage)
    }

    private fun titleOnFocusedChunk(grid: MultiEpgGrid, unixSec: Long): String =
        titleOnFocusedChunkOrNull(grid, unixSec)
            ?: error(gridDump(grid, unixSec))

    private fun titleOnFocusedChunkOrNull(grid: MultiEpgGrid, unixSec: Long): String? {
        val chunk = MultiEpgWindows.chunkContaining(unixSec)
        for (channel in grid.state.value.channels) {
            val bars = channel.bars.overlapping(chunk.startSec, chunk.endSec)
            if (bars.isNotEmpty()) {
                return bars.first().event.title
            }
        }
        return null
    }

    private fun gridDump(grid: MultiEpgGrid, unixSec: Long): String {
        val chunk = MultiEpgWindows.chunkContaining(unixSec)
        val bars = grid.state.value.channels.joinToString { ch ->
            ch.bars.joinToString { "${it.event.title}:${it.startSec}-${it.endSec}" }
        }
        return "no bar in chunk ${chunk.startSec} " +
            "windows=${grid.loadedWindowStarts} " +
            "origin=${grid.state.value.originFloorSec} bars=[$bars]"
    }

    private fun programme(
        id: String,
        title: String,
        start: Long,
        duration: String = "3600",
        serviceReference: String = "1:0:1:1:1:1:0:0:0:0:",
        serviceName: String = "TV"
    ): Event = Event(
        eventId = id,
        title = title,
        start = start.toString(),
        duration = duration,
        serviceReference = serviceReference,
        serviceName = serviceName
    )

    private suspend fun MultiEpgGrid.awaitState(
        timeoutMs: Long = 5_000L,
        dump: () -> String = { "" },
        condition: () -> Boolean
    ) {
        // The grid publishes every change on [MultiEpgGrid.state]; wake on each emission.
        withTimeoutOrNull(timeoutMs) { state.first { condition() } }
            ?: error("timed out waiting for grid condition ${dump()}")
    }
}
