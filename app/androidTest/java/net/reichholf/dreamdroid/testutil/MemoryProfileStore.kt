package net.reichholf.dreamdroid.testutil

import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ProfileStore

/**
 * Profiles in memory, so a screen test's ViewModel can save and activate without
 * touching the app's database or its remembered active profile.
 */
class MemoryProfileStore(rows: List<Profile> = emptyList()) : ProfileStore {
    private val rows = rows.toMutableList()
    private var remembered = -1

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

fun memoryProfiles(vararg rows: Profile): ProfileRepository =
    ProfileRepository(MemoryProfileStore(rows.toList()))
