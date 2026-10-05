package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsPlugin
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.room.toTimer
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** The timer list to show, or why there is none. */
sealed interface TimerListResult {
    data class Loaded(val timers: List<Timer>) : TimerListResult

    data class Failed(val message: UiText) : TimerListResult
}

/**
 * Recording locations and tags the receiver offers for a timer. [locationsFromReceiver] is
 * false when [locations] is the `/hdd/movie` stand-in for a failed request.
 */
data class TimerChoices(
    val locations: List<String>,
    val tags: List<String>,
    val locationsFromReceiver: Boolean
)

/**
 * Timers of the active profile. A live `/web/timerlist` replaces the profile's Room
 * snapshot; the snapshot stands in when the receiver cannot answer.
 *
 * Each request's client learns whether the receiver has the VPS plugin ([vpsPlugin]); the
 * answer comes from [ReceiverPluginsRepository], asked before the first timer request of a
 * profile, and a client that finds the plugin gone marks it absent there.
 */
@Singleton
class TimerRepository @Inject constructor(
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase,
    private val plugins: ReceiverPluginsRepository,
    private val sessions: SessionConnectionHolder
) {
    /**
     * The timers of the active profile, read [cacheFirstLoad] from the Room snapshot, which a
     * live `/web/timerlist` replaces.
     */
    fun timers(forceRefresh: Boolean = false): Flow<TimerListResult> = cacheFirstLoad(
        sessions,
        forceRefresh,
        cached = { snapshot() },
        fetch = {
            val profileId = profiles.requireCurrent().id
            clients.current(vpsPlugin = vpsPlugin()).timers().also { response ->
                val live = response.value
                if (live != null && profileId != null) {
                    database.timerDao().replaceSnapshot(
                        profileId,
                        live.mapIndexed { index, timer -> timer.toListEntity(profileId, index) }
                    )
                }
            }
        },
        loaded = { timers, _ -> TimerListResult.Loaded(timers) },
        failed = { error ->
            TimerListResult.Failed(
                error?.contentErrorText() ?: UiText.Resource(R.string.error_parsing)
            )
        }
    )

    /**
     * Saves [timer]. With [original], the receiver replaces that timer instead of adding one.
     * Its [Timer.vps] goes along when known.
     */
    suspend fun save(timer: Timer, original: Timer?): EnigmaResponse<SimpleResult> {
        val api = clients.current(vpsPlugin = vpsPlugin())
        return if (original == null) api.addTimer(timer) else api.editTimer(original, timer)
    }

    /** Enables a disabled [timer] and disables an enabled one; its VPS stays. */
    suspend fun toggleEnabled(timer: Timer): EnigmaResponse<SimpleResult> =
        clients.current(vpsPlugin = vpsPlugin())
            .setTimerDisabled(timer, disabled = timer.disabled != "1")

    /**
     * Adds a timer for [event] by its event id; the receiver fills in the rest. With the VPS
     * plugin, the timer gets the profile's VPS default.
     */
    suspend fun addByEvent(event: Event): EnigmaResponse<SimpleResult> {
        val vpsPlugin = vpsPlugin()
        val vps = vpsForNewTimer(vpsPlugin.present)
        return clients.current(vpsPlugin = vpsPlugin).addTimerForEvent(event, vps)
    }

    /**
     * VPS for a timer the user creates: the profile's default when the receiver has the VPS
     * plugin, null when it has not.
     */
    suspend fun vpsForNewTimer(): TimerVps? = vpsForNewTimer(hasVpsPlugin())

    /** Whether the active profile's receiver has the VPS plugin, asking it while unknown. */
    suspend fun hasVpsPlugin(): Boolean = (plugins.known() ?: plugins.refresh().value)?.vps == true

    suspend fun delete(timer: Timer): EnigmaResponse<SimpleResult> =
        clients.current().deleteTimer(timer)

    /** Removes finished timers on the receiver. */
    suspend fun cleanup(): EnigmaResponse<SimpleResult> = clients.current().cleanupTimers()

    /**
     * Locations and tags, fetched from the receiver until it answered. Failed locations
     * read as `/hdd/movie`, failed tags as none; both are asked again on the next call.
     * Answers are kept until the profile changes; known ones return without suspending.
     * While the session is Offline the receiver is not asked, as if it had failed.
     */
    suspend fun locationsAndTags(): TimerChoices {
        if (!profiles.locationsLoadedFromReceiver() || profiles.tags().isEmpty()) {
            val profile = profiles.requireCurrent()
            val api = if (sessions.status.value.shouldSkipReceiverHttp(hasCache = true)) {
                null
            } else {
                clients.forProfile(profile)
            }
            if (!profiles.locationsLoadedFromReceiver()) {
                profiles.putLocations(profile, api?.locations()?.value)
            }
            if (profiles.tags().isEmpty()) {
                profiles.putTags(profile, api?.tags()?.value)
            }
        }
        return TimerChoices(
            profiles.locations().toList(),
            profiles.tags().toList(),
            profiles.locationsLoadedFromReceiver()
        )
    }

    /** The last timer list the receiver sent for the active profile, or null if none. */
    suspend fun snapshot(): List<Timer>? = snapshot(profiles.requireCurrent().id)

    private fun vpsForNewTimer(vpsPlugin: Boolean): TimerVps? =
        if (vpsPlugin) TimerVps(profiles.requireCurrent().vpsDefault) else null

    /** The VPS plugin for one request's client; a request that finds it gone marks it absent. */
    private suspend fun vpsPlugin(): VpsPlugin {
        val profile = profiles.requireCurrent()
        return VpsPlugin(hasVpsPlugin()) { plugins.markVpsAbsent(profile) }
    }

    private suspend fun snapshot(profileId: Int?): List<Timer>? = profileId?.let { id ->
        database.timerDao().snapshot(id)?.map { it.toTimer() }
    }
}
