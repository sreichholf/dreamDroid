package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.UiText

/** Whether the active profile's receiver has the AutoTimer plugin. */
enum class PluginPresence {
    /** Not asked yet for this profile, or the question failed before any answer. */
    Unknown,
    Present,
    Absent
}

/** The AutoTimer list, or why there is none. */
sealed interface AutoTimerLoad {
    data class Ready(val entries: List<AutoTimerEntry>) : AutoTimerLoad

    data object PluginMissing : AutoTimerLoad

    data class Failed(val message: UiText) : AutoTimerLoad
}

/**
 * The AutoTimer plugin of the active profile's receiver (`/autotimer`). Online only: the box
 * changes its AutoTimers itself, so nothing is cached but whether the plugin is there.
 */
@Singleton
class AutoTimerRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository
) {
    /** Plugin presence per profile id; the last answer stays while a receiver is offline. */
    private val known = MutableStateFlow<Map<Int, PluginPresence>>(emptyMap())

    val presence: Flow<PluginPresence> =
        combine(profiles.current, known) { profile, known ->
            profile?.id?.let { known[it] } ?: PluginPresence.Unknown
        }.distinctUntilChanged()

    /** Emits the active profile's id, and again each time the active profile changes. */
    val profileId: Flow<Int?> = profiles.current.map { it?.id }.distinctUntilChanged()

    /**
     * Asks `/web/external` whether the plugin is installed. A failed request keeps the last
     * answer for the profile.
     */
    suspend fun refreshPresence(): PluginPresence = checkPresence().value ?: currentPresence()

    suspend fun list(): AutoTimerLoad {
        if (currentPresence() != PluginPresence.Present) {
            val check = checkPresence()
            when (check.value) {
                null -> return AutoTimerLoad.Failed(check.error.contentErrorText())
                PluginPresence.Absent -> return AutoTimerLoad.PluginMissing
                else -> Unit
            }
        }
        val response = clients.current().getAutoTimers()
        return response.value?.let { AutoTimerLoad.Ready(it) }
            ?: AutoTimerLoad.Failed(response.error.contentErrorText())
    }

    private fun currentPresence(): PluginPresence =
        profiles.current.value?.id?.let { known.value[it] } ?: PluginPresence.Unknown

    /** The receiver's answer, remembered for the profile; no value when the request failed. */
    private suspend fun checkPresence(): EnigmaResponse<PluginPresence> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        val response = clients.forProfile(profile).getWebExternals()
        val paths = response.value ?: return EnigmaResponse(null, response.error)
        val presence = if (PLUGIN_PATH in paths) PluginPresence.Present else PluginPresence.Absent
        profile.id?.let { id -> known.update { it + (id to presence) } }
        return EnigmaResponse(presence)
    }

    private companion object {
        /** The plugin's API; `autotimereditor` is its web page. */
        const val PLUGIN_PATH = "autotimer"
    }
}
