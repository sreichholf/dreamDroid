package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.PreviewMatch
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.enigma.autotimer.toParams
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.NameValuePair
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
    /** [defaults] are the settings the box gives a new AutoTimer; null if unreadable. */
    data class Ready(val entries: List<AutoTimerEntry>, val defaults: AutoTimerSettings?) :
        AutoTimerLoad

    data object PluginMissing : AutoTimerLoad

    data class Failed(val message: UiText) : AutoTimerLoad
}

/** How a write went. */
sealed interface AutoTimerWriteResult {
    /** The box took it; [message] is its (localized) reply. */
    data class Done(val message: UiText) : AutoTimerWriteResult

    /** The id no longer names the AutoTimer that was loaded; nothing was written. */
    data object Conflict : AutoTimerWriteResult

    data class Failed(val message: UiText) : AutoTimerWriteResult
}

/** A preview of one AutoTimer, or why there is none. */
sealed interface AutoTimerPreviewLoad {
    data class Ready(val autoTimer: AutoTimer, val matches: List<PreviewMatch>) :
        AutoTimerPreviewLoad

    /** The box previews enabled AutoTimers only. */
    data class Disabled(val autoTimer: AutoTimer) : AutoTimerPreviewLoad

    /** The id names no AutoTimer called that any more, or one dreamDroid cannot read. */
    data object Gone : AutoTimerPreviewLoad

    data object PluginMissing : AutoTimerPreviewLoad

    /** The plugin failed while searching. */
    data class PluginFailed(val autoTimer: AutoTimer, val message: String) : AutoTimerPreviewLoad

    data class Failed(val message: UiText) : AutoTimerPreviewLoad
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
    private val writes = Mutex()

    /** Plugin presence per profile id; the last answer stays while a receiver is offline. */
    private val known = MutableStateFlow<Map<Int, PluginPresence>>(emptyMap())

    val presence: Flow<PluginPresence> =
        combine(profiles.current, known) { profile, known ->
            profile?.id?.let { known[it] } ?: PluginPresence.Unknown
        }.distinctUntilChanged()

    private val _revision = MutableStateFlow(0)

    /**
     * Counts the writes sent to the box, whatever their outcome, so a screen that shows
     * AutoTimers knows to list them again after a write made elsewhere.
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

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
        return response.value?.let { AutoTimerLoad.Ready(it.entries, it.defaults) }
            ?: AutoTimerLoad.Failed(response.error.contentErrorText())
    }

    /**
     * What the AutoTimer [id] called [name] would record. The fresh list first confirms the id
     * still names it; a disabled one is not sent to the box, which would answer with nothing.
     */
    suspend fun preview(id: AutoTimerId, name: String): AutoTimerPreviewLoad {
        val entries = when (val load = list()) {
            is AutoTimerLoad.Ready -> load.entries
            AutoTimerLoad.PluginMissing -> return AutoTimerPreviewLoad.PluginMissing
            is AutoTimerLoad.Failed -> return AutoTimerPreviewLoad.Failed(load.message)
        }
        val autoTimer = (entries.firstOrNull { it.id == id } as? AutoTimerEntry.Readable)
            ?.autoTimer
            ?.takeIf { it.settings.name == name }
            ?: return AutoTimerPreviewLoad.Gone
        if (!autoTimer.settings.enabled) {
            return AutoTimerPreviewLoad.Disabled(autoTimer)
        }
        val http = clients.currentHttp().apply { setConnectionTimeoutMillis(PREVIEW_TIMEOUT_MS) }
        val response = EnigmaClient(http).testAutoTimer(id.value)
        return when (val outcome = response.value) {
            is PreviewOutcome.Matches -> AutoTimerPreviewLoad.Ready(autoTimer, outcome.matches)

            is PreviewOutcome.PluginFailed ->
                AutoTimerPreviewLoad.PluginFailed(autoTimer, outcome.message)

            null -> AutoTimerPreviewLoad.Failed(response.error.contentErrorText())
        }
    }

    /**
     * Writes [write] unless the box renumbered its AutoTimers since they were loaded. One
     * write runs at a time.
     */
    suspend fun save(write: AutoTimerWrite): AutoTimerWriteResult = counted {
        writes.withLock {
            when (write) {
                is AutoTimerWrite.Change ->
                    guard(AutoTimerEntry.Readable(write.loaded))
                        ?: result(clients.current().editAutoTimer(write.toParams()))

                is AutoTimerWrite.Create ->
                    result(clients.current().editAutoTimer(write.toParams()))
            }
        }
    }

    /**
     * Searches the EPG for all enabled AutoTimers now and adds timers for new matches, as a
     * run on the box does. It gets its own client with a long timeout, so other requests do
     * not cancel it.
     */
    suspend fun runNow(): AutoTimerWriteResult {
        val http = clients.currentHttp().apply { setConnectionTimeoutMillis(RUN_TIMEOUT_MS) }
        return counted { result(EnigmaClient(http).runAutoTimers()) }
    }

    /**
     * The AutoTimer the box lists for [settings] after a save: the one with that id when
     * [id] is set, else the newest one with that match and name. The box does not answer a
     * create with the new id.
     */
    suspend fun locate(settings: AutoTimerSettings, id: AutoTimerId?): AutoTimer? {
        val entries = (list() as? AutoTimerLoad.Ready)?.entries ?: return null
        val name = settings.name.ifBlank { settings.match }
        return entries.filterIsInstance<AutoTimerEntry.Readable>()
            .map { it.autoTimer }
            .filter { if (id != null) it.id == id else it.settings.match == settings.match }
            .filter { it.settings.name == name }
            .maxByOrNull { it.id.value }
    }

    suspend fun setEnabled(autoTimer: AutoTimer, enabled: Boolean): AutoTimerWriteResult =
        save(AutoTimerWrite.Change(autoTimer, autoTimer.settings.copy(enabled = enabled)))

    /** Removes [entry] unless its id names another AutoTimer by now. */
    suspend fun remove(entry: AutoTimerEntry): AutoTimerWriteResult = counted {
        writes.withLock {
            guard(entry) ?: result(
                clients.current()
                    .removeAutoTimer(listOf(NameValuePair("id", entry.id.value.toString())))
            )
        }
    }

    /**
     * Runs [write], then counts it in [revision]. A refused write counts too: its conflict
     * means a shown list is out of date.
     */
    private inline fun counted(write: () -> AutoTimerWriteResult): AutoTimerWriteResult = try {
        write()
    } finally {
        _revision.update { it + 1 }
    }

    /**
     * Null when the box still lists [expected] under its id. The box numbers its AutoTimers
     * anew whenever its config file changed, so an id alone may name another one.
     */
    private suspend fun guard(expected: AutoTimerEntry): AutoTimerWriteResult? {
        val response = clients.current().getAutoTimers()
        val entries = response.value?.entries
            ?: return AutoTimerWriteResult.Failed(response.error.contentErrorText())
        val current = entries.firstOrNull { it.id == expected.id }
        return if (current == expected) null else AutoTimerWriteResult.Conflict
    }

    private fun result(response: EnigmaResponse<SimpleResult>): AutoTimerWriteResult =
        if (response.value != null && response.error == null) {
            AutoTimerWriteResult.Done(response.userMessageText())
        } else {
            AutoTimerWriteResult.Failed(response.userMessageText())
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

        /** The box searches the whole EPG for a preview; that can take a while. */
        const val PREVIEW_TIMEOUT_MS = 60_000

        /** Longer than the 50 s between the keep-alives the box sends during a run. */
        const val RUN_TIMEOUT_MS = 120_000
    }
}
