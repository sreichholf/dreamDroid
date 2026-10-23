package net.reichholf.dreamdroid.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastLiveStreamRepositoryTest {
    private val profiles = TestProfiles().repository
    private val preferences = MemorySharedPreferences()
    private val repository = LastLiveStreamRepository(preferences, profiles)
    private val news = LastLiveStream(
        reference = "1:0:1:1:2:3:4:0:0:0:",
        serviceName = "News HD",
        bouquetRef = "1:7:1:0:0:0:0:0:0:0:"
    )

    @Test
    fun returnsTheLastChannelForTheActiveProfile() {
        activate(4)

        repository.save(news)

        assertEquals(news, lastStream())
    }

    /**
     * An earlier development build saved the programme title under "last_stream_title"; the
     * channel name has its own key, so that title never shows as the channel.
     */
    @Test
    fun theChannelNameIsNotReadFromTheOldTitleKey() {
        activate(4)
        repository.save(news)
        preferences.edit().putString("last_stream_title", "Das 1% Quiz").commit()

        assertEquals("News HD", lastStream()?.serviceName)
        assertEquals("News HD", preferences.getString("last_stream_service_name", null))
    }

    @Test
    fun anotherProfileDoesNotGetTheChannel() {
        activate(4)
        repository.save(news)

        activate(5)

        assertNull(lastStream())
    }

    @Test
    fun oneSlotForAllProfiles() {
        activate(4)
        repository.save(news)
        activate(5)
        repository.save(news.copy(reference = "1:0:1:9:9:9:9:0:0:0:", serviceName = "Sport"))

        activate(4)

        assertNull(lastStream())
    }

    @Test
    fun blankReferencesAreNotSaved() {
        activate(4)

        repository.save(news.copy(reference = ""))

        assertNull(lastStream())
    }

    @Test
    fun nothingIsSavedWithoutAnActiveProfile() {
        repository.save(news)
        activate(-1)

        assertNull(lastStream())
    }

    @Test
    fun theFlowFollowsSavesAndProfileSwitches() = runTest(UnconfinedTestDispatcher()) {
        activate(4)
        val seen = mutableListOf<LastLiveStream?>()
        backgroundScope.launch { repository.lastStream.collect { seen += it } }
        val sport = news.copy(reference = "1:0:1:9:9:9:9:0:0:0:", serviceName = "Sport")

        repository.save(news)
        repository.save(sport)
        activate(5)
        activate(4)

        assertEquals(listOf(null, news, sport, null, sport), seen)
    }

    private fun lastStream(): LastLiveStream? = runBlocking { repository.lastStream.first() }

    private fun activate(id: Int) {
        profiles.setCurrent(Profile().apply { this.id = id })
    }
}
