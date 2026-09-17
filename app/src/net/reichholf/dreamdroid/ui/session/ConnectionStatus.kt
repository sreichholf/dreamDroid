package net.reichholf.dreamdroid.ui.session

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Session connectivity for the phone shell. [Session.Online] / [Session.Offline] are stored;
 * Checking is progress only ([checking] / [Chip.Checking]) and is not a stored session word.
 *
 * [lastFailure] keeps Auth vs Unreachable distinct so later slices can grey writes without
 * an Auth chip. The drawer chip is only Online / Offline / Checking.
 */
data class ConnectionStatus(
    val session: Session? = null,
    val lastUpdatedMs: Long? = null,
    val lastFailure: EnigmaFailure? = null,
    val checking: Boolean = false
) {
    enum class Session {
        Online,
        Offline
    }

    enum class Chip {
        Online,
        Offline,
        Checking
    }

    val chip: Chip
        get() = when {
            checking -> Chip.Checking
            session == Session.Online -> Chip.Online
            session == Session.Offline -> Chip.Offline
            else -> Chip.Checking
        }

    fun chipLabelRes(): Int = when (chip) {
        Chip.Online -> R.string.session_online
        Chip.Offline -> R.string.session_offline
        Chip.Checking -> R.string.session_checking
    }

    /**
     * Zap, remote keys, power, and other writes need [Session.Online]. Checking with a
     * stored Online session stays writable; Offline and Auth (stored as Offline) block.
     */
    val blocksMutations: Boolean
        get() = session != Session.Online
}

/**
 * Whether this profile has a use-driven cache that can paint a hub start surface
 * (TV/Radio tab strip, Movies location strip, or Timer snapshot).
 *
 * Later slices **extend** this with additional sources; they must not replace it with
 * a weaker check. MultiEPG chunks alone must not skip the ProfileCheck gate.
 */
fun hasUseDrivenCache(
    tabStripRefs: Collection<String>,
    hasMovieLocationStrip: Boolean = false,
    hasTimerSnapshot: Boolean = false
): Boolean = tabStripRefs.isNotEmpty() || hasMovieLocationStrip || hasTimerSnapshot

fun hasUseDrivenCache(profile: Profile, context: Context): Boolean {
    val id = profile.id ?: return false
    return runBlocking(Dispatchers.IO) {
        val db = AppDatabase.database(context)
        hasUseDrivenCache(
            db.rosterDao().getTabStripRefs(id),
            db.movieDao().locationMetaCount(id) > 0,
            db.timerDao().snapshotCount(id) > 0
        )
    }
}

fun hasUseDrivenCache(profile: Profile): Boolean {
    val context = DreamDroid.getAppContext() ?: return false
    return hasUseDrivenCache(profile, context)
}

/** Skip the checking gate when the TV/Radio tab strip can paint the start route. */
fun shouldShowProfileCheckCheckingUi(hasCache: Boolean): Boolean = !hasCache

/**
 * Unreachable/Auth with cache stay Offline on the start route. Other kinds keep the
 * failed ProfileCheck gate even when cache exists.
 */
fun shouldShowProfileCheckFailedUi(hasCache: Boolean, failure: EnigmaFailure?): Boolean {
    if (!hasCache) {
        return true
    }
    return failure == null || !failure.allowsOfflineSession()
}

/**
 * Unreachable (except illegal host/port) and Auth can browse cache as Offline.
 * Illegal host/port has no useful cache. Other kinds stay on the ProfileCheck gate.
 */
fun EnigmaFailure.allowsOfflineSession(): Boolean = when (this) {
    is EnigmaFailure.Auth -> true

    is EnigmaFailure.Unreachable ->
        reason != EnigmaFailure.UnreachableReason.IllegalHost

    else -> false
}

/**
 * Process-wide session holder the phone shell (MainActivity / PhoneNavHost) reads.
 * Tests should construct a fresh instance rather than [shared].
 */
class SessionConnectionHolder {
    private val statusState = MutableStateFlow(ConnectionStatus())

    val status: StateFlow<ConnectionStatus> = statusState.asStateFlow()

    fun beginChecking() {
        statusState.value = statusState.value.copy(checking = true)
    }

    fun cancelChecking() {
        val current = statusState.value
        if (current.checking) {
            statusState.value = current.copy(checking = false)
        }
    }

    fun resetForProfileChange() {
        statusState.value = ConnectionStatus(checking = true)
    }

    fun onSuccess(nowMs: Long = System.currentTimeMillis()) {
        statusState.value = ConnectionStatus(
            session = ConnectionStatus.Session.Online,
            lastUpdatedMs = nowMs,
            lastFailure = null,
            checking = false
        )
    }

    fun onFailure(failure: EnigmaFailure, hasCache: Boolean) {
        if (failure is EnigmaFailure.Cancelled) {
            cancelChecking()
            return
        }
        val current = statusState.value
        val goOffline = hasCache && failure.allowsOfflineSession()
        statusState.value = ConnectionStatus(
            session = if (goOffline) {
                ConnectionStatus.Session.Offline
            } else {
                null
            },
            lastUpdatedMs = current.lastUpdatedMs,
            lastFailure = failure,
            checking = false
        )
    }

    fun applyProfileCheckResult(
        result: ProfileCheckResult,
        hasCache: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (result.hasError && !result.isSoftError) {
            val failure = result.failure ?: EnigmaFailure.Unknown(null)
            onFailure(failure, hasCache)
        } else {
            onSuccess(nowMs)
        }
    }

    companion object {
        val shared = SessionConnectionHolder()
    }
}
