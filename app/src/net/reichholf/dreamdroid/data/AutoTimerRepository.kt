package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.di.ApplicationScope
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerApi
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.PreviewMatch
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
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
    /**
     * The box took it; [message] is its (localized) reply. [id] is the AutoTimer a save wrote,
     * when the plugin says (api_version 1.7).
     */
    data class Done(val message: UiText, val id: AutoTimerId? = null) : AutoTimerWriteResult

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
 * changes its AutoTimers itself, so nothing is cached but whether the plugin is there, and
 * which API it speaks ([AutoTimerApi]), learned with that answer.
 */
@Singleton
class AutoTimerRepository @Inject constructor(
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository,
    /** Where an API 1.7 run, which answers only once done, goes on unawaited. */
    @param:ApplicationScope private val background: CoroutineScope
) {
    private val writes = Mutex()

    /** The plugin per profile id; the last answer stays while a receiver is offline. */
    private val known = MutableStateFlow<Map<Int, AutoTimerPlugin>>(emptyMap())

    val presence: Flow<PluginPresence> =
        combine(profiles.current, known) { profile, known ->
            profile?.id?.let { known[it] }.presence()
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
     * Asks the receiver whether the plugin is installed. A failed request keeps the last
     * answer for the profile.
     */
    suspend fun refreshPresence(): PluginPresence =
        (checkPlugin().value ?: knownPlugin()).presence()

    suspend fun list(): AutoTimerLoad {
        if (knownPlugin() !is AutoTimerPlugin.Installed) {
            val check = checkPlugin()
            when (check.value) {
                null -> return AutoTimerLoad.Failed(check.error.contentErrorText())
                AutoTimerPlugin.Missing -> return AutoTimerLoad.PluginMissing
                is AutoTimerPlugin.Installed -> Unit
            }
        }
        val response = clients.current().autoTimers()
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
        val response = clients.current(PREVIEW_TIMEOUT_MS).testAutoTimer(id)
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
                        ?: saved(clients.current().saveAutoTimer(write))

                is AutoTimerWrite.Create -> saved(clients.current().saveAutoTimer(write))
            }
        }
    }

    /**
     * Searches the EPG for all enabled AutoTimers now and adds timers for new matches, as a
     * run on the box does, which rewrites the box's AutoTimers. It gets its own client with a
     * long timeout, so other requests do not cancel it.
     *
     * Where the plugin keeps the connection alive ([AutoTimerApi.runAnswersInTime]), writes
     * wait for the run and the result is its summary. Otherwise the run is only started: the
     * plugin answers once done, which can take longer than any timeout. [revision] counts it
     * again when the box answers or the request gives up, so a shown list is fetched anew.
     */
    suspend fun runNow(): AutoTimerWriteResult {
        val plugin = knownPlugin() ?: checkPlugin().let { check ->
            check.value ?: return AutoTimerWriteResult.Failed(check.error.contentErrorText())
        }
        val api = (plugin as? AutoTimerPlugin.Installed)?.api
            ?: return AutoTimerWriteResult.Failed(UiText.Resource(R.string.autotimer_not_installed))
        val client = clients.current(RUN_TIMEOUT_MS)
        if (api.runAnswersInTime) {
            return counted { writes.withLock { result(client.runAutoTimers()) } }
        }
        background.launch {
            try {
                client.runAutoTimers()
            } finally {
                _revision.update { it + 1 }
            }
        }
        return counted {
            AutoTimerWriteResult.Done(UiText.Resource(R.string.autotimer_run_started))
        }
    }

    /**
     * The AutoTimer the box lists for [settings] after a save: the one with that id when
     * [id] is set, else the newest one with that match and name. Only api_version 1.7
     * answers a create with the new id ([AutoTimerWriteResult.Done.id]).
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
            guard(entry) ?: result(clients.current().removeAutoTimer(entry.id))
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
        val response = clients.current().autoTimers()
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

    /** [result], with the id the box gave the AutoTimer it wrote. */
    private fun saved(response: EnigmaResponse<SimpleResult>): AutoTimerWriteResult =
        when (val written = result(response)) {
            is AutoTimerWriteResult.Done ->
                written.copy(id = response.value?.id?.toIntOrNull()?.let(::AutoTimerId))

            else -> written
        }

    private fun knownPlugin(): AutoTimerPlugin? =
        profiles.current.value?.id?.let { known.value[it] }

    /** The receiver's answer, remembered for the profile; no value when the request failed. */
    private suspend fun checkPlugin(): EnigmaResponse<AutoTimerPlugin> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        val response = clients.forProfile(profile).autoTimerPlugin()
        val plugin = response.value ?: return EnigmaResponse(null, response.error)
        profile.id?.let { id -> known.update { it + (id to plugin) } }
        return EnigmaResponse(plugin)
    }

    private companion object {
        /** The box searches the whole EPG for a preview; that can take a while. */
        const val PREVIEW_TIMEOUT_MS = 60_000

        /** Longer than the 50 s between the keep-alives the box sends during a run. */
        const val RUN_TIMEOUT_MS = 120_000
    }
}

private fun AutoTimerPlugin?.presence(): PluginPresence = when (this) {
    null -> PluginPresence.Unknown
    AutoTimerPlugin.Missing -> PluginPresence.Absent
    is AutoTimerPlugin.Installed -> PluginPresence.Present
}
