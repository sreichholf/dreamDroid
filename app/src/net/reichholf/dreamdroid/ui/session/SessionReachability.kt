package net.reichholf.dreamdroid.ui.session

import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult

/** Foreground session recheck. Same cadence as the hub `/web/getcurrent` poll. */
const val SESSION_REACHABILITY_INTERVAL_MS: Long = 30_000L

/**
 * Persistent box ping while the phone shell is resumed.
 *
 * Run when the session is [ConnectionStatus.Session.Online] (the box may die) or
 * [ConnectionStatus.Session.Offline] after Unreachable (it may come back).
 *
 * Skip when Checking, Auth (credentials will not fix themselves), illegal host/port,
 * or other non-reachability failures (HTTP/parse) that belong on Recheck / the gate.
 */
fun shouldProbeReachability(status: ConnectionStatus): Boolean {
    if (status.checking) {
        return false
    }
    return when (status.session) {
        ConnectionStatus.Session.Online -> true
        ConnectionStatus.Session.Offline -> status.lastFailure.isReachabilityFailure()
        null -> false
    }
}

fun EnigmaFailure?.isReachabilityFailure(): Boolean = when (this) {
    null -> true

    is EnigmaFailure.Unreachable ->
        reason != EnigmaFailure.UnreachableReason.IllegalHost

    else -> false
}

/**
 * Run [check] when [shouldProbeReachability] is true. Returns true if a check ran.
 * Callers must fetch live (clear `cachedDeviceInfo`); a cached XML hit is not a recheck.
 */
suspend fun probeSessionReachabilityIfNeeded(
    holder: SessionConnectionHolder,
    hasCache: Boolean,
    nowMs: Long = System.currentTimeMillis(),
    isBusy: () -> Boolean = { false },
    check: suspend () -> ProfileCheckResult
): Boolean {
    if (isBusy()) {
        return false
    }
    if (!shouldProbeReachability(holder.status.value)) {
        return false
    }
    holder.applyProfileCheckResult(check(), hasCache, nowMs)
    return true
}
