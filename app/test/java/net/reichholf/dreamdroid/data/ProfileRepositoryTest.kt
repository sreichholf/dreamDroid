package net.reichholf.dreamdroid.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileRepositoryTest {
    @Test
    fun switchingProfileEmitsOnceAndClearsCaches() {
        val first = profile(1, "living-room")
        val second = profile(2, "bedroom")
        val repo = ProfileRepository(MemoryProfileStore(listOf(first, second)))
        assertTrue(repo.activate(first.id!!, forceEvent = true))
        repo.locations().add("/hdd/movie")
        repo.tags().add("News")
        repo.setDeviceInfo(first, "<deviceinfo/>")
        repo.setLocationsLoadedFromReceiver(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val currentIds = mutableListOf<Int?>()
        val switchedIds = mutableListOf<Int>()
        scope.launch { repo.current.collect { currentIds.add(it?.id) } }
        scope.launch { repo.switches.collect { switchedIds.add(it.id!!) } }
        assertEquals(listOf(1), currentIds)
        assertTrue(switchedIds.isEmpty())

        assertTrue(repo.activate(second.id!!, forceEvent = true))

        assertEquals(listOf(1, 2), currentIds)
        assertEquals(listOf(2), switchedIds)
        assertTrue(repo.locations().isEmpty())
        assertTrue(repo.tags().isEmpty())
        assertFalse(repo.locationsLoadedFromReceiver())
        assertNull(repo.deviceInfo(first))
        assertNull(repo.deviceInfo(second))
        scope.cancel()
    }

    @Test
    fun replacingCurrentProfileKeepsPerProfileCaches() {
        val first = profile(1, "living-room")
        val edited = profile(1, "living-room").apply { name = "Living Room" }
        val repo = ProfileRepository(MemoryProfileStore(listOf(first)))
        assertTrue(repo.activate(first.id!!, forceEvent = true))
        repo.locations().add("/hdd/movie")
        repo.setDeviceInfo(first, "<deviceinfo/>")

        repo.setCurrent(edited)

        assertEquals("Living Room", repo.requireCurrent().name)
        assertEquals(listOf("/hdd/movie"), repo.locations())
        assertEquals("<deviceinfo/>", repo.deviceInfo(edited))
    }
}

private fun profile(id: Int, host: String): Profile = Profile().apply {
    this.id = id
    this.host = host
    name = host
}

private class MemoryProfileStore(rows: List<Profile>) : ProfileStore {
    private val rows = rows.toMutableList()

    override fun profiles(): List<Profile> = rows.toList()

    override fun profile(id: Int): Profile? = rows.firstOrNull { it.id == id }

    override fun add(profile: Profile): Long {
        val nextId = (rows.mapNotNull { it.id }.maxOrNull() ?: 0) + 1
        profile.id = nextId
        rows.add(profile)
        return nextId.toLong()
    }

    override fun delete(profile: Profile) {
        rows.removeAll { it.id == profile.id }
    }
}
