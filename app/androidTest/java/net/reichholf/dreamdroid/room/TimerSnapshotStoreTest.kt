package net.reichholf.dreamdroid.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerListLoadResult
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgTimerClock
import net.reichholf.dreamdroid.multiepg.buildMultiEpgTimerClocks
import net.reichholf.dreamdroid.multiepg.multiEpgTimerClockKey
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.services.HubTimerListSession
import net.reichholf.dreamdroid.ui.services.TimerListState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerSnapshotStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TimerDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        dao = db.timerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun writeAndReadSnapshot() = runBlocking {
        val timers = listOf(sampleTimer(name = "News"), sampleTimer(name = "Sport"))
        TimerSnapshotStore.replace(dao, PROFILE, timers)
        val loaded = TimerSnapshotStore.load(dao, PROFILE)
        assertEquals(listOf("News", "Sport"), loaded?.map { it.name })
        assertEquals("Das Erste HD", loaded!![0].serviceName)
        assertEquals("1:0:1:6DCA:44C:1:C00000:0:0:0:", loaded[0].reference)
        assertEquals("1476644933", loaded[0].begin)
        assertNull(TimerSnapshotStore.load(dao, OTHER_PROFILE))
    }

    @Test
    fun writtenEmptyIsEmptyListNotMissing() = runBlocking {
        TimerSnapshotStore.replace(dao, PROFILE, emptyList())
        assertEquals(emptyList<Timer>(), TimerSnapshotStore.load(dao, PROFILE))
        assertNull(TimerSnapshotStore.load(dao, OTHER_PROFILE))
    }

    @Test
    fun replaceOnWriteReplacesRows() = runBlocking {
        TimerSnapshotStore.replace(
            dao,
            PROFILE,
            listOf(sampleTimer(name = "Old"), sampleTimer(name = "Drop"))
        )
        TimerSnapshotStore.replace(dao, PROFILE, listOf(sampleTimer(name = "New")))
        val loaded = TimerSnapshotStore.load(dao, PROFILE)
        assertEquals(listOf("New"), loaded?.map { it.name })
        assertEquals(1, dao.getTimerList(PROFILE).size)
    }

    @Test
    fun snapshotFeedsMultiEpgTimerClocks() = runBlocking {
        val start = 1_704_117_600L
        val timer = sampleTimer(
            name = "News",
            begin = start.toString(),
            end = (start + 1800).toString(),
            justPlay = "0"
        )
        TimerSnapshotStore.replace(dao, PROFILE, listOf(timer))
        val loaded = TimerSnapshotStore.load(dao, PROFILE)!!
        val ref = timer.reference
        val clocks = buildMultiEpgTimerClocks(
            channels = listOf(
                MultiEpgChannel(
                    serviceRef = ref,
                    serviceName = "TV",
                    bars = listOf(
                        MultiEpgBar(
                            event = Event(
                                eventId = "10",
                                title = "News",
                                start = start.toString(),
                                duration = "1800",
                                serviceReference = ref,
                                serviceName = "TV"
                            ),
                            startSec = start,
                            endSec = start + 1800
                        )
                    )
                )
            ),
            timers = loaded
        )
        assertEquals(
            MultiEpgTimerClock.Record,
            clocks[multiEpgTimerClockKey(ref, "10", start)]
        )
    }

    @Test
    fun successfulHttpWritesSnapshot() = runBlocking {
        val session = timerSession(this)
        session.loadTimers = {
            TimerListLoadResult(true, listOf(sampleTimer(name = "News")), null)
        }
        session.loadAndApply(session.beginLoad())
        val loaded = TimerSnapshotStore.load(dao, PROFILE)
        assertEquals(listOf("News"), loaded?.map { it.name })
        assertEquals(listOf("News"), session.listState!!.items.map { it.name })
    }

    @Test
    fun failedHttpDoesNotWriteSnapshot() = runBlocking {
        val session = timerSession(this)
        var emptyMessage: String? = null
        session.onEmptyMessage = { emptyMessage = it }
        session.loadTimers = {
            TimerListLoadResult(false, listOf(sampleTimer(name = "Ghost")), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        assertNull(TimerSnapshotStore.load(dao, PROFILE))
        assertEquals(0, dao.snapshotCount(PROFILE))
        assertEquals("timeout", emptyMessage)
        assertEquals(emptyList<String>(), session.listState!!.items.map { it.name })
    }

    @Test
    fun failedHttpPaintsSnapshotWithoutRewriting() = runBlocking {
        val original = listOf(sampleTimer(name = "News"), sampleTimer(name = "Sport"))
        TimerSnapshotStore.replace(dao, PROFILE, original)
        val session = timerSession(this)
        var emptyMessage: String? = "stale"
        session.onEmptyMessage = { emptyMessage = it }
        session.loadTimers = {
            TimerListLoadResult(false, emptyList(), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        val loaded = TimerSnapshotStore.load(dao, PROFILE)
        assertEquals(listOf("News", "Sport"), loaded?.map { it.name })
        assertEquals(listOf("News", "Sport"), session.listState!!.items.map { it.name })
        assertNull(emptyMessage)
    }

    @Test
    fun failedHttpOnWrittenEmptyKeepsNoListItemCopy() = runBlocking {
        TimerSnapshotStore.replace(dao, PROFILE, emptyList())
        val session = timerSession(this)
        var emptyMessage: String? = null
        session.onEmptyMessage = { emptyMessage = it }
        session.loadTimers = {
            TimerListLoadResult(false, emptyList(), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        assertEquals(emptyList<Timer>(), TimerSnapshotStore.load(dao, PROFILE))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(context.getString(R.string.no_list_item), emptyMessage)
        assertEquals(emptyList<String>(), session.listState!!.items.map { it.name })
    }

    private fun timerSession(scope: kotlinx.coroutines.CoroutineScope): HubTimerListSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = HubTimerListSession()
        session.context = context
        session.listState = TimerListState()
        session.refresh = ComposeRefreshState()
        session.scope = scope
        session.timerDao = dao
        session.profileId = PROFILE
        return session
    }

    private fun sampleTimer(
        name: String,
        begin: String = "1476644933",
        end: String = "1476649083",
        justPlay: String = "0"
    ): Timer = Timer(
        reference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
        serviceName = "Das Erste HD",
        eit = "10",
        name = name,
        description = "desc",
        begin = begin,
        end = end,
        duration = "4150",
        beginReadable = "16 Oct 2016 20:08",
        endReadable = "16 Oct 2016 21:18",
        justPlay = justPlay,
        state = "0",
        disabled = "0",
        repeated = "0"
    )

    companion object {
        private const val PROFILE = 7
        private const val OTHER_PROFILE = 8
    }
}
