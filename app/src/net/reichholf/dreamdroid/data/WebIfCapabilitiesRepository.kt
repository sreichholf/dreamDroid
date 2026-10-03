package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.WebIfCapabilities

/**
 * The [WebIfCapabilities] of each saved profile's receiver, in memory. The profile check sets
 * them; `EnigmaHttp` flips [WebIfCapabilities.postRequest] on a 405. A profile not checked yet,
 * and a draft without an id, has the defaults. [ProfileRepository] drops an entry with the
 * device info it came from.
 */
@Singleton
class WebIfCapabilitiesRepository @Inject constructor() {
    private val byProfile = MutableStateFlow<Map<Int, WebIfCapabilities>>(emptyMap())

    /** Capabilities by profile id, for observers; [of] reads one profile's. */
    val all: StateFlow<Map<Int, WebIfCapabilities>> = byProfile.asStateFlow()

    fun of(profile: Profile): WebIfCapabilities =
        profile.id?.let { byProfile.value[it] } ?: WebIfCapabilities()

    fun set(profile: Profile, capabilities: WebIfCapabilities) {
        val id = profile.id ?: return
        byProfile.update { it + (id to capabilities) }
    }

    fun setPostRequest(profile: Profile, enabled: Boolean) {
        val id = profile.id ?: return
        byProfile.update { all ->
            all + (id to (all[id] ?: WebIfCapabilities()).copy(postRequest = enabled))
        }
    }

    /** Forgets [profile]'s entry: back to the defaults until its next check. */
    fun drop(profile: Profile) {
        val id = profile.id ?: return
        byProfile.update { it - id }
    }

    /** Forgets every profile's entry. */
    fun clear() {
        byProfile.value = emptyMap()
    }
}
