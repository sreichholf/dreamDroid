package net.reichholf.dreamdroid.multiepg

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.EnigmaFailureException
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.room.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { titleOnFocusedChunkOrNull(session, t0) != null }
        assertEquals("T1", titleOnFocusedChunk(session, t0))
        assertTrue(session.syncing)
        gate.complete(Unit)
        session.awaitIdle()
        assertEquals("T2", titleOnFocusedChunk(session, t0))
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
                listOf(programme(id = "e-$time", title = "Old", start = time))
            },
            clockMs = { now },
            ttlMs = 1_000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        now += 2_000L
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("Old", titleOnFocusedChunk(session, t0))
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
            ttlMs = 25L * 60L * 1000L
        )
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
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
            ttlMs = 25L * 60L * 1000L
        )
        sync.ensureChunk(1, "bouquet-a", t0)
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { session.channels.isNotEmpty() }
        assertEquals("T", titleOnFocusedChunk(session, t0))
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
            ttlMs = 25L * 60L * 1000L
        )
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("A", titleOnFocusedChunk(session, t0))
        session.replaceAndLoad("bouquet-b", t0)
        assertTrue(session.channels.isEmpty())
        session.awaitIdle()
        assertEquals("B", titleOnFocusedChunk(session, t0))
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", lateNow)
        session.awaitIdle()
        assertEquals(lateNow, session.timelineStartSec)
        assertEquals(lateNow, session.originFloorSec)
        assertTrue(session.timelineEndSec > chunk.endSec)
        assertEquals(chunk.startSec, session.loadedWindowStarts.minOrNull())
        assertFalse(
            session.loadedWindowStarts.contains(chunk.startSec - MultiEpgWindows.CHUNK_SECONDS)
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        assertTrue(session.loadedWindowStarts.contains(chunk.startSec))

        session.onVisibleWindow(now, now + 7200L)
        session.awaitIdle()
        assertTrue(
            "panning inside the padded window must keep today",
            session.loadedWindowStarts.contains(chunk.startSec)
        )

        session.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        session.awaitIdle()
        assertFalse(session.loadedWindowStarts.contains(chunk.startSec))
        assertTrue(session.timelineStartSec >= chunk.endSec)

        val fetchesBeforeRestore = fetches.size
        session.onVisibleWindow(now, now + 7200L)
        session.awaitIdle()
        assertTrue(session.loadedWindowStarts.contains(chunk.startSec))
        assertEquals(now, session.timelineStartSec)
        assertEquals(
            "restoring today should peek Room, not refetch the box",
            fetchesBeforeRestore,
            fetches.size
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        session.onVisibleWindow(now, now + 7200L)
        session.awaitIdle()
        session.focusAt(now - MultiEpgWindows.CHUNK_SECONDS)
        session.awaitIdle()
        assertFalse(fetches.contains(yesterday))
        assertFalse(session.loadedWindowStarts.contains(yesterday))
        assertEquals(now, session.originFloorSec)
        assertEquals(now, session.timelineStartSec)
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        assertEquals(earliest, session.timelineStartSec)
        assertEquals(now, session.originFloorSec)
        assertFalse(fetches.contains(yesterday))
        assertFalse(session.loadedWindowStarts.contains(yesterday))
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        assertFalse(session.syncing)

        session.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        waitUntil { session.syncing }
        assertTrue(session.channels.isNotEmpty())
        gate.complete(Unit)
        session.awaitIdle()
        assertFalse(session.syncing)
        assertTrue(session.loadedWindowStarts.contains(day2))
        assertTrue(session.channels.isNotEmpty())
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
            fetchTimers = {
                gate.await()
                error("timerlist down")
            }
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { session.channels.isNotEmpty() }
        assertTrue(session.timerClocks.isEmpty())
        gate.complete(Unit)
        session.awaitIdle()
        assertTrue(session.channels.isNotEmpty())
        assertTrue(session.timerClocks.isEmpty())
        assertEquals(null, session.errorMessage)
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
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
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        waitUntil(dump = { gridDump(session, t0) + " clocks=${session.timerClocks}" }) {
            session.timerClocks.isNotEmpty()
        }
        assertEquals(MultiEpgTimerClock.Record, session.timerClocks.values.single())
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
            loadBouquetServices = {
                listOf(
                    Service(withEpg, "Das Erste"),
                    Service(withoutEpg, "ZDF")
                )
            }
        )
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        assertEquals(2, session.channels.size)
        assertEquals("Das Erste", session.channels[0].serviceName)
        assertEquals("ZDF", session.channels[1].serviceName)
        assertTrue(session.channels[1].bars.isNotEmpty())

        session.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        session.awaitIdle()
        assertEquals(2, session.channels.size)
        assertEquals("ZDF", session.channels[1].serviceName)
        assertTrue(
            "ZDF stays in the grid after today is dropped",
            session.channels[1].bars.isEmpty()
        )
        assertTrue(session.channels[0].bars.isNotEmpty())
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", origin)
        session.awaitIdle()
        session.onVisibleWindow(origin, origin + 4L * 3600L)
        session.awaitIdle()
        val spanBars = session.channels.single().bars.filter { it.event.eventId == "span" }
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
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
        session.replaceAndLoad("bouquet-a", now)
        session.awaitIdle()
        assertEquals(2, session.channels.size)
        assertEquals("ZDF", session.channels[1].serviceName)
        assertEquals(null, session.errorMessage)

        session.load(now, forceRefresh = true, isPull = true)
        session.awaitIdle()
        assertEquals(2, session.channels.size)
        assertEquals("ZDF", session.channels[1].serviceName)
        assertEquals("getservices down", session.errorMessage)
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
            persistBouquet = { false },
            shouldSkipReceiverHttp = { hasCache -> hasCache },
            loadBouquetServices = {
                bouquetCalls.incrementAndGet()
                hang.await()
                error("getservices down")
            }
        )
        session.replaceAndLoad("bouquet-a", t0)
        waitUntil { titleOnFocusedChunkOrNull(session, t0) != null }
        assertEquals("Cached", titleOnFocusedChunk(session, t0))
        assertEquals(1, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, session.errorMessage)
        session.cancel()
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
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
        session.replaceAndLoad(bouquet, t0)
        waitUntil { titleOnFocusedChunkOrNull(session, t0) != null }
        assertEquals("HubFill", titleOnFocusedChunk(session, t0))
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, session.errorMessage)
        session.cancel()
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
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
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, session.errorMessage)
        session.cancel()
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad(bouquet, t0)
        waitUntil { titleOnFocusedChunkOrNull(session, t0) != null }
        session.awaitIdle()
        assertTrue(fetches.get() >= 1)
        assertEquals("Live", titleOnFocusedChunk(session, t0))
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
            persistBouquet = { false },
            isSessionOffline = { true },
            loadBouquetServices = {
                throw EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            }
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("Cached", titleOnFocusedChunk(session, t0))
        assertEquals(null, session.errorMessage)

        session.load(t0, forceRefresh = true, isPull = true)
        session.awaitIdle()
        assertEquals("Cached", titleOnFocusedChunk(session, t0))
        assertEquals(null, session.errorMessage)
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet",
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
        session.replaceAndLoad(bouquet, t0)
        waitUntil { titleOnFocusedChunkOrNull(session, t0) != null }
        session.awaitIdle()
        assertEquals("HubFill", titleOnFocusedChunk(session, t0))
        val day2 = MultiEpgWindows.chunkContaining(t0).startSec +
            2L * MultiEpgWindows.CHUNK_SECONDS
        session.onVisibleWindow(day2 + 3600L, day2 + 3600L + 7200L)
        session.awaitIdle()
        assertEquals(0, fetches.get())
        assertEquals(0, bouquetCalls.get())
        assertEquals(null, session.errorMessage)
        session.cancel()
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
        val session = MultiEpgSession(
            sync = sync,
            scope = this,
            profileId = { 1 },
            noBouquetMessage = "no bouquet"
        )
        session.replaceAndLoad("bouquet-a", t0)
        session.awaitIdle()
        assertEquals("T", titleOnFocusedChunk(session, t0))
        assertTrue(session.channels.isNotEmpty())
        assertEquals("prefetch down", session.errorMessage)
    }

    private fun titleOnFocusedChunk(session: MultiEpgSession, unixSec: Long): String =
        titleOnFocusedChunkOrNull(session, unixSec)
            ?: error(gridDump(session, unixSec))

    private fun titleOnFocusedChunkOrNull(session: MultiEpgSession, unixSec: Long): String? {
        val chunk = MultiEpgWindows.chunkContaining(unixSec)
        for (channel in session.channels) {
            val bars = channel.bars.overlapping(chunk.startSec, chunk.endSec)
            if (bars.isNotEmpty()) {
                return bars.first().event.title
            }
        }
        return null
    }

    private fun gridDump(session: MultiEpgSession, unixSec: Long): String {
        val chunk = MultiEpgWindows.chunkContaining(unixSec)
        val bars = session.channels.joinToString { ch ->
            ch.bars.joinToString { "${it.event.title}:${it.startSec}-${it.endSec}" }
        }
        return "no bar in chunk ${chunk.startSec} " +
            "windows=${session.loadedWindowStarts} " +
            "origin=${session.originFloorSec} bars=[$bars]"
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

    private suspend fun waitUntil(
        timeoutMs: Long = 5_000L,
        dump: () -> String = { "" },
        condition: () -> Boolean
    ) {
        val startMs = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - startMs > timeoutMs) {
                error("timed out waiting for session condition ${dump()}")
            }
            delay(10)
        }
    }
}
