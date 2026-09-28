package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerRequests
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.room.toTimer
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
 */
@Singleton
class TimerRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase
) {
    /**
     * The receiver's timers, or the snapshot when that request fails. With
     * [preferSnapshot], an existing snapshot answers without asking the receiver.
     */
    suspend fun timers(preferSnapshot: Boolean = false): TimerListResult {
        val profileId = profiles.requireCurrent().id
        if (preferSnapshot) {
            snapshot(profileId)?.let { return TimerListResult.Loaded(it) }
        }
        val response = clients.current().getTimers()
        val live = response.value
        if (live != null) {
            if (profileId != null) {
                database.timerDao().replaceSnapshot(
                    profileId,
                    live.mapIndexed { index, timer -> timer.toListEntity(profileId, index) }
                )
            }
            return TimerListResult.Loaded(live)
        }
        snapshot(profileId)?.let { return TimerListResult.Loaded(it) }
        val message = response.error?.contentErrorText() ?: UiText.Resource(R.string.error_parsing)
        return TimerListResult.Failed(message)
    }

    /** Saves [timer]. With [original], the receiver replaces that timer instead of adding one. */
    suspend fun save(timer: Timer, original: Timer?): EnigmaResponse<SimpleResult> =
        clients.current().changeTimer(TimerRequests.getSaveParams(timer, original))

    /** Enables a disabled [timer] and disables an enabled one. */
    suspend fun toggleEnabled(timer: Timer): EnigmaResponse<SimpleResult> =
        save(timer.copy(disabled = if (timer.disabled == "1") "0" else "1"), timer)

    /** Adds a timer for [event] by its event id; the receiver fills in the rest. */
    suspend fun addByEvent(event: Event): EnigmaResponse<SimpleResult> =
        clients.current().addTimerByEventId(TimerRequests.getEventIdParams(event))

    suspend fun delete(timer: Timer): EnigmaResponse<SimpleResult> =
        clients.current().deleteTimer(TimerRequests.getDeleteParams(timer))

    /** Removes finished timers on the receiver. */
    suspend fun cleanup(): EnigmaResponse<SimpleResult> = clients.current().cleanupTimers()

    /**
     * Locations and tags, fetched from the receiver until it answered. Failed locations
     * read as `/hdd/movie`, failed tags as none; both are asked again on the next call.
     * Answers are kept until the profile changes; known ones return without suspending.
     */
    suspend fun locationsAndTags(): TimerChoices {
        if (!profiles.locationsLoadedFromReceiver() || profiles.tags().isEmpty()) {
            val http = EnigmaHttp(profiles.requireCurrent())
            withContext(Dispatchers.IO) {
                if (!profiles.locationsLoadedFromReceiver()) {
                    profiles.loadLocations(http)
                }
                if (profiles.tags().isEmpty()) {
                    profiles.loadTags(http)
                }
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

    private suspend fun snapshot(profileId: Int?): List<Timer>? = profileId?.let { id ->
        database.timerDao().snapshot(id)?.map { it.toTimer() }
    }
}
