package net.reichholf.dreamdroid.data

import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LastLiveStreamRepositoryTest {
    private val profiles = TestProfiles().repository
    private val repository = LastLiveStreamRepository(MemorySharedPreferences(), profiles)
    private val news = LastLiveStream(
        reference = "1:0:1:1:2:3:4:0:0:0:",
        serviceName = "News HD",
        bouquetRef = "1:7:1:0:0:0:0:0:0:0:"
    )

    @Test
    fun returnsTheLastChannelForTheActiveProfile() {
        activate(4)

        repository.save(news)

        assertEquals(news, repository.forActiveProfile())
    }

    @Test
    fun anotherProfileDoesNotGetTheChannel() {
        activate(4)
        repository.save(news)

        activate(5)

        assertNull(repository.forActiveProfile())
    }

    @Test
    fun oneSlotForAllProfiles() {
        activate(4)
        repository.save(news)
        activate(5)
        repository.save(news.copy(reference = "1:0:1:9:9:9:9:0:0:0:", serviceName = "Sport"))

        activate(4)

        assertNull(repository.forActiveProfile())
    }

    @Test
    fun blankReferencesAreNotSaved() {
        activate(4)

        repository.save(news.copy(reference = ""))

        assertNull(repository.forActiveProfile())
    }

    @Test
    fun nothingIsSavedWithoutAnActiveProfile() {
        repository.save(news)
        activate(-1)

        assertNull(repository.forActiveProfile())
    }

    private fun activate(id: Int) {
        profiles.setCurrent(Profile().apply { this.id = id })
    }
}
