package net.reichholf.dreamdroid.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgTimerClock
import net.reichholf.dreamdroid.multiepg.buildMultiEpgTimerClocks
import net.reichholf.dreamdroid.multiepg.multiEpgTimerClockKey
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.room.toTimer
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.LOCATIONS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TAGS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_ADD_BY_EVENT_ID
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CHANGE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_CLEANUP
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_DELETE
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.TIMER_LIST
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TimerRepository] against a [TestReceiver] and an in-memory database. */
class TimerRepositoryTest {
    private val receiver = TestReceiver()
    private val dao = receiver.profiles.database.timerDao()
    private val repository = receiver.timerRepository()

    @BeforeEach
    fun setUp() {
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.shutdown()
    }

    @Test
    fun liveListReplacesTheSnapshot() = runTest {
        writeSnapshot(listOf(timer("Old"), timer("Dropped")))
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))

        val result = repository.timers()

        val names = listOf("Navy CIS: L.A.", "Tagesschau")
        assertEquals(names, (result as TimerListResult.Loaded).timers.map { it.name })
        assertEquals(names, snapshot()?.map { it.name })
        assertEquals("SAT.1 HD", snapshot()!![0].serviceName)
    }

    @Test
    fun failedListPaintsTheSnapshotWithoutRewritingIt() = runTest {
        writeSnapshot(listOf(timer("News"), timer("Sport")))
        receiver.fail(TIMER_LIST)

        val result = repository.timers()

        assertEquals(
            listOf("News", "Sport"),
            (result as TimerListResult.Loaded).timers.map { it.name }
        )
        assertEquals(listOf("News", "Sport"), snapshot()?.map { it.name })
    }

    @Test
    fun failedListWithoutSnapshotReportsTheError() = runTest {
        receiver.fail(TIMER_LIST)

        val result = repository.timers()

        val error = EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error"))
        assertEquals(TimerListResult.Failed(error.contentErrorText()), result)
        assertNull(snapshot())
        assertEquals(0, dao.snapshotCount(PROFILE_ID))
    }

    @Test
    fun unreadableListWithoutSnapshotIsAParseError() = runTest {
        receiver.respond(TIMER_LIST, "<e2timerlist><e2timer><e2name>Cut")

        val result = repository.timers()

        assertEquals(TimerListResult.Failed(UiText.Resource(R.string.error_parsing)), result)
    }

    @Test
    fun emptySnapshotIsAnEmptyListNotAnError() = runTest {
        writeSnapshot(emptyList())
        receiver.fail(TIMER_LIST)

        assertEquals(TimerListResult.Loaded(emptyList()), repository.timers())
    }

    @Test
    fun preferredSnapshotAnswersWithoutTheReceiver() = runTest {
        writeSnapshot(listOf(timer("Cached")))
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))

        val result = repository.timers(preferSnapshot = true)

        assertEquals(listOf("Cached"), (result as TimerListResult.Loaded).timers.map { it.name })
        assertEquals(0, receiver.requestsTo(TIMER_LIST).size)
    }

    @Test
    fun preferredSnapshotFallsBackToTheReceiverWhenThereIsNone() = runTest {
        receiver.respond(TIMER_LIST, loadWebFixture("timerlist.xml"))

        val result = repository.timers(preferSnapshot = true)

        assertEquals(2, (result as TimerListResult.Loaded).timers.size)
        assertEquals(2, snapshot()?.size)
    }

    @Test
    fun snapshotFeedsMultiEpgTimerClocks() = runTest {
        val start = 1_704_117_600L
        val recording = timer("News").copy(
            begin = start.toString(),
            end = (start + 1800).toString()
        )
        writeSnapshot(listOf(recording))
        val ref = recording.reference

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
            timers = snapshot()!!
        )

        assertEquals(MultiEpgTimerClock.Record, clocks[multiEpgTimerClockKey(ref, "10", start)])
    }

    @Test
    fun saveReplacesTheOriginalTimer() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer changed"))
        val original = timer("Old")

        val response = repository.save(original.copy(name = "New"), original)

        assertEquals(Python.TRUE, response.value?.state)
        val url = receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!
        assertEquals("New", url.queryParameter("name"))
        assertEquals("1", url.queryParameter("deleteOldOnSave"))
        assertEquals(original.reference, url.queryParameter("channelOld"))
        assertEquals(original.begin, url.queryParameter("beginOld"))
    }

    @Test
    fun saveWithoutOriginalAddsATimer() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer added"))

        repository.save(timer("New"), null)

        val url = receiver.requestsTo(TIMER_CHANGE).single().requestUrl!!
        assertEquals("0", url.queryParameter("deleteOldOnSave"))
        assertNull(url.queryParameter("channelOld"))
    }

    @Test
    fun toggleFlipsTheDisabledFlag() = runTest {
        receiver.respond(TIMER_CHANGE, simpleResult(true, "Timer changed"))

        repository.toggleEnabled(timer("On").copy(disabled = "0"))
        repository.toggleEnabled(timer("Off").copy(disabled = "1"))

        assertEquals(
            listOf("1", "0"),
            receiver.requestsTo(TIMER_CHANGE).map { it.requestUrl!!.queryParameter("disabled") }
        )
    }

    @Test
    fun addByEventSendsTheEventId() = runTest {
        receiver.respond(TIMER_ADD_BY_EVENT_ID, simpleResult(true, "Timer added"))
        val event = Event(eventId = "39150", serviceReference = "1:0:1:6DCA:44C:1:C00000:0:0:0:")

        assertEquals("Timer added", repository.addByEvent(event).value?.stateText)

        val url = receiver.requestsTo(TIMER_ADD_BY_EVENT_ID).single().requestUrl!!
        assertEquals("39150", url.queryParameter("eventid"))
        assertEquals(event.serviceReference, url.queryParameter("sRef"))
    }

    @Test
    fun deleteAndCleanupReachTheReceiver() = runTest {
        receiver.respond(TIMER_DELETE, simpleResult(true, "Timer deleted"))
        receiver.respond(TIMER_CLEANUP, simpleResult(true, "Timer list cleaned"))
        val deleted = timer("Gone")

        assertEquals("Timer deleted", repository.delete(deleted).value?.stateText)
        assertEquals("Timer list cleaned", repository.cleanup().value?.stateText)
        val url = receiver.requestsTo(TIMER_DELETE).single().requestUrl!!
        assertEquals(deleted.reference, url.queryParameter("sRef"))
        assertEquals(
            "true",
            receiver.requestsTo(TIMER_CLEANUP).single().requestUrl!!.queryParameter("cleanup")
        )
    }

    @Test
    fun locationsAndTagsAreFetchedOnce() = runTest {
        receiver.respond(
            LOCATIONS,
            "<e2locations><e2location>/media/hdd/</e2location></e2locations>"
        )
        receiver.respond(TAGS, "<e2tags><e2tag>News</e2tag><e2tag>Sport</e2tag></e2tags>")

        val first = repository.locationsAndTags()
        val second = repository.locationsAndTags()

        assertEquals(
            TimerChoices(listOf("/media/hdd/"), listOf("News", "Sport"), true),
            first
        )
        assertEquals(first, second)
        assertEquals(1, receiver.requestsTo(LOCATIONS).size)
        assertEquals(1, receiver.requestsTo(TAGS).size)
    }

    @Test
    fun failedLocationsFallBackToHddMovie() = runTest {
        receiver.fail(LOCATIONS)
        receiver.fail(TAGS)

        assertEquals(
            TimerChoices(listOf("/hdd/movie"), emptyList(), false),
            repository.locationsAndTags()
        )
    }

    @Test
    fun failedLocationsAreAskedAgain() = runTest {
        receiver.fail(LOCATIONS)
        receiver.respond(TAGS, "<e2tags><e2tag>News</e2tag></e2tags>")
        repository.locationsAndTags()
        receiver.respond(
            LOCATIONS,
            "<e2locations><e2location>/media/hdd/</e2location></e2locations>"
        )

        val choices = repository.locationsAndTags()

        assertEquals(TimerChoices(listOf("/media/hdd/"), listOf("News"), true), choices)
        assertEquals(2, receiver.requestsTo(LOCATIONS).size)
        assertEquals(1, receiver.requestsTo(TAGS).size)
    }

    @Test
    fun slowLocationsDoNotHoldTheProfileRepository() = runBlocking {
        receiver.respond(LOCATIONS, LOCATIONS_BODY)
        receiver.respond(TAGS, "<e2tags><e2tag>News</e2tag></e2tags>")
        val held = receiver.hold(LOCATIONS)
        val profiles = receiver.repository
        val profile = profiles.requireCurrent()

        val choices = async(Dispatchers.IO) { repository.locationsAndTags() }
        awaitArrival(held)
        // Runs while the receiver still holds the locations answer; it must not wait for it.
        val deviceInfo = try {
            withTimeout(5_000L) {
                async(Dispatchers.IO) {
                    profiles.setDeviceInfo(profile, DeviceInfo())
                    profiles.deviceInfo(profile)
                }.await()
            }
        } finally {
            held.release()
        }

        assertEquals(DeviceInfo(), deviceInfo)
        assertEquals(
            TimerChoices(listOf("/media/hdd/"), listOf("News"), true),
            choices.await()
        )
    }

    @Test
    fun locationsOfTheProfileSwitchedAwayFromAreDropped() = runBlocking {
        receiver.respond(LOCATIONS, LOCATIONS_BODY)
        receiver.respond(TAGS, "<e2tags><e2tag>News</e2tag></e2tags>")
        val held = receiver.hold(LOCATIONS)
        val profiles = receiver.repository
        val other = Profile().apply {
            name = "other"
            host = "other-box"
        }
        profiles.save(other)

        val choices = async(Dispatchers.IO) { repository.locationsAndTags() }
        awaitArrival(held)
        assertTrue(profiles.activate(other.id!!, forceEvent = true))
        held.release()
        choices.await()

        assertTrue(profiles.locations().isEmpty())
        assertTrue(profiles.tags().isEmpty())
        assertEquals(false, profiles.locationsLoadedFromReceiver())
    }

    private suspend fun awaitArrival(hold: TestReceiver.Hold) = withContext(Dispatchers.IO) {
        assertTrue(hold.arrived.await(5, TimeUnit.SECONDS), "the request never arrived")
    }

    private suspend fun writeSnapshot(timers: List<Timer>) {
        dao.replaceSnapshot(
            PROFILE_ID,
            timers.mapIndexed { index, timer -> timer.toListEntity(PROFILE_ID, index) }
        )
    }

    private suspend fun snapshot(): List<Timer>? = dao.snapshot(PROFILE_ID)?.map { it.toTimer() }

    private fun timer(name: String) = Timer(
        reference = "1:0:1:6DCA:44C:1:C00000:0:0:0:",
        serviceName = "Das Erste HD",
        eit = "10",
        name = name,
        begin = "1476644933",
        end = "1476649083",
        justPlay = "0",
        disabled = "0",
        state = "0",
        repeated = "0"
    )

    private companion object {
        const val LOCATIONS_BODY =
            "<e2locations><e2location>/media/hdd/</e2location></e2locations>"
    }
}
