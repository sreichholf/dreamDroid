package net.reichholf.dreamdroid.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test
    fun editingConnectionSettingsDropsDeviceInfoOnly() {
        val first = profile(1, "living-room")
        val repo = ProfileRepository(MemoryProfileStore(listOf(first)))
        assertTrue(repo.activate(first.id!!, forceEvent = true))
        repo.locations().add("/hdd/movie")
        repo.setDeviceInfo(first, "<deviceinfo/>")

        repo.setCurrent(profile(1, "other-box"))

        assertEquals("other-box", repo.requireCurrent().host)
        assertNull(repo.deviceInfo(repo.requireCurrent()))
        assertEquals(listOf("/hdd/movie"), repo.locations())
    }

    @Test
    fun switchesKeepLatestWhenCollectorIsBehind() {
        val profiles = (1..3).map { profile(it, "box-$it") }
        val repo = ProfileRepository(MemoryProfileStore(profiles))
        val switchedIds = mutableListOf<Int>()
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                repo.switches.collect {
                    gate.await()
                    switchedIds.add(it.id!!)
                }
            }
            assertTrue(repo.activate(1, forceEvent = true))
            yield()
            assertTrue(repo.activate(2, forceEvent = true))
            assertTrue(repo.activate(3, forceEvent = true))
            gate.complete(Unit)
            yield()
            yield()
            collector.cancel()
        }
        assertEquals(3, switchedIds.last())
    }

    @Test
    fun setCurrentRemembersTheActiveProfile() {
        val store = MemoryProfileStore(listOf(profile(1, "a"), profile(2, "b")))
        val repo = ProfileRepository(store)

        assertTrue(repo.setCurrent(2))
        assertFalse(repo.setCurrent(9))

        assertEquals(2, store.remembered)
        assertEquals(2, repo.activeProfileId())
        val restarted = ProfileRepository(store)
        restarted.loadCurrent()
        assertEquals(2, restarted.requireCurrent().id)
    }

    @Test
    fun savingActiveProfileReplacesCurrentWithoutSwitch() {
        val store = MemoryProfileStore(listOf(profile(1, "living-room")))
        val repo = ProfileRepository(store)
        assertTrue(repo.setCurrent(1, forceEvent = true))
        val switchedIds = mutableListOf<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.launch { repo.switches.collect { switchedIds.add(it.id!!) } }

        repo.save(profile(1, "living-room").apply { name = "Living Room" })
        val added = profile(0, "bedroom").apply { id = null }
        repo.save(added)

        assertEquals("Living Room", repo.requireCurrent().name)
        assertEquals("Living Room", store.profile(1)?.name)
        assertEquals(2, added.id)
        assertEquals(1, repo.requireCurrent().id)
        assertTrue(switchedIds.isEmpty())
        scope.cancel()
    }

    @Test
    fun deletingActiveProfileActivatesAnother() {
        val store = MemoryProfileStore(listOf(profile(1, "keep"), profile(2, "gone")))
        val repo = ProfileRepository(store)
        assertTrue(repo.setCurrent(2, forceEvent = true))

        repo.delete(store.profile(2)!!)

        assertEquals(listOf(2), store.deletedIds)
        assertEquals(1, repo.requireCurrent().id)
        assertEquals(1, store.remembered)
    }

    @Test
    fun deletingLastProfileForgetsTheActiveOne() {
        val store = MemoryProfileStore(listOf(profile(1, "only")))
        val repo = ProfileRepository(store)
        assertTrue(repo.setCurrent(1, forceEvent = true))

        repo.delete(store.profile(1)!!)

        assertEquals(-1, store.remembered)
        assertNull(repo.requireCurrent().id)
        assertNull(repo.activeProfileId())
    }

    @Test
    fun deletingAnotherProfileKeepsTheActiveOne() {
        val store = MemoryProfileStore(listOf(profile(1, "active"), profile(2, "other")))
        val repo = ProfileRepository(store)
        assertTrue(repo.setCurrent(1, forceEvent = true))

        repo.delete(store.profile(2)!!)

        assertEquals(1, repo.requireCurrent().id)
        assertEquals(1, store.remembered)
    }
}

private fun profile(id: Int, host: String): Profile = Profile().apply {
    this.id = id
    this.host = host
    name = host
}

private class MemoryProfileStore(rows: List<Profile>) : ProfileStore {
    private val rows = rows.toMutableList()
    var remembered: Int = -1
    val deletedIds = mutableListOf<Int>()

    override fun profiles(): List<Profile> = rows.toList()

    override fun profile(id: Int): Profile? = rows.firstOrNull { it.id == id }

    override fun add(profile: Profile): Long {
        val nextId = (rows.mapNotNull { it.id }.maxOrNull() ?: 0) + 1
        profile.id = nextId
        rows.add(profile)
        return nextId.toLong()
    }

    override fun update(profile: Profile) {
        rows.replaceAll { if (it.id == profile.id) profile else it }
    }

    override fun delete(profile: Profile) {
        rows.removeAll { it.id == profile.id }
        profile.id?.let { deletedIds.add(it) }
    }

    override fun activeId(): Int = remembered

    override fun setActiveId(id: Int) {
        remembered = id
    }

    override fun clearActiveId() {
        remembered = -1
    }

    override fun xmlDebug(): Boolean = false

    override fun legacyProfile(): Profile = Profile.getDefault()
}
