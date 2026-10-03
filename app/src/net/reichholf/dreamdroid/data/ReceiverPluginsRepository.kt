package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.ReceiverPlugins
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin

/** Whether the active profile's receiver has a plugin. */
enum class PluginPresence {
    /** Not asked yet for this profile, or the question failed before any answer. */
    Unknown,
    Present,
    Absent
}

/**
 * The [ReceiverPlugins] of each profile's receiver, from [refresh]. The last answer stays
 * while a receiver is offline; a request that finds the VPS plugin gone corrects it
 * ([markVpsAbsent]).
 */
@Singleton
class ReceiverPluginsRepository @Inject constructor(
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository
) {
    /** The answer per profile id. */
    private val known = MutableStateFlow<Map<Int, ReceiverPlugins>>(emptyMap())

    /** The active profile's plugins, again each time they or the profile change. */
    private val current: Flow<ReceiverPlugins?> =
        combine(profiles.current, known) { profile, known ->
            profile?.id?.let { known[it] }
        }.distinctUntilChanged()

    /** Whether the active profile's receiver has the AutoTimer plugin. */
    val autoTimerPresence: Flow<PluginPresence> =
        current.map { plugins ->
            when (plugins?.autoTimer) {
                null -> PluginPresence.Unknown
                AutoTimerPlugin.Missing -> PluginPresence.Absent
                is AutoTimerPlugin.Installed -> PluginPresence.Present
            }
        }.distinctUntilChanged()

    /** The last answer for the active profile; null before one. */
    fun known(): ReceiverPlugins? = profiles.current.value?.id?.let { known.value[it] }

    /**
     * Asks the active profile's receiver which plugins it has and remembers the answer. A
     * failed request has no value and keeps the last answer.
     */
    suspend fun refresh(): EnigmaResponse<ReceiverPlugins> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        val response = clients.forProfile(profile).plugins()
        val plugins = response.value ?: return EnigmaResponse(null, response.error)
        profile.id?.let { id -> known.update { it + (id to plugins) } }
        return EnigmaResponse(plugins)
    }

    /** Records that [profile]'s receiver no longer serves the VPS plugin. */
    fun markVpsAbsent(profile: Profile) {
        val id = profile.id ?: return
        known.update { known ->
            val plugins = known[id] ?: return@update known
            known + (id to plugins.copy(vps = false))
        }
    }
}
