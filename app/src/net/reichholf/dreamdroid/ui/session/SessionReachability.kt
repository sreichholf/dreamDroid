package net.reichholf.dreamdroid.ui.session

import net.reichholf.dreamdroid.enigma.ProfileCheckResult

/** Foreground Offline recheck. Same cadence as the hub `/web/getcurrent` poll. */
const val OFFLINE_REACHABILITY_INTERVAL_MS: Long = 30_000L

/** Recheck the box only while Offline; Online and in-flight Checking stay put. */
fun shouldProbeReachability(status: ConnectionStatus): Boolean =
    !status.checking && status.session == ConnectionStatus.Session.Offline

/**
 * Run [check] when the session is Offline and idle. Returns true if it became Online.
 * Callers must fetch live (clear `cachedDeviceInfo`); a cached XML hit is not a recheck.
 */
suspend fun probeOfflineSessionIfNeeded(
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
    return holder.status.value.session == ConnectionStatus.Session.Online
}
