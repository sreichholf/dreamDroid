package net.reichholf.dreamdroid.ui.session

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReachabilityTest {
    @Test
    fun probesOnlineAndUnreachableOfflineOnly() {
        assertFalse(shouldProbeReachability(ConnectionStatus()))
        assertTrue(
            shouldProbeReachability(
                ConnectionStatus(session = ConnectionStatus.Session.Online)
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Online,
                    checking = true
                )
            )
        )
        assertTrue(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    lastFailure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.Timeout
                    )
                )
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    lastFailure = EnigmaFailure.Auth
                )
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    lastFailure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.IllegalHost
                    )
                )
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    lastFailure = EnigmaFailure.Http(500, "Server Error")
                )
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    checking = true,
                    lastFailure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.Dns
                    )
                )
            )
        )
    }

    @Test
    fun probeSkipsAuthOfflineWithoutCallingCheck() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onFailure(EnigmaFailure.Auth, hasCache = true)
        var calls = 0
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 2L,
            check = {
                calls += 1
                ProfileCheckResult()
            }
        )
        assertFalse(ran)
        assertEquals(0, calls)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
        assertEquals(EnigmaFailure.Auth, holder.status.value.lastFailure)
    }

    @Test
    fun probeSkipsCheckingAndBusy() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns),
            hasCache = true
        )
        holder.beginChecking()
        var calls = 0
        assertFalse(
            probeSessionReachabilityIfNeeded(
                holder = holder,
                hasCache = true,
                nowMs = 3L,
                check = {
                    calls += 1
                    ProfileCheckResult()
                }
            )
        )
        holder.cancelChecking()
        assertFalse(
            probeSessionReachabilityIfNeeded(
                holder = holder,
                hasCache = true,
                nowMs = 4L,
                isBusy = { true },
                check = {
                    calls += 1
                    ProfileCheckResult()
                }
            )
        )
        assertEquals(0, calls)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
    }

    @Test
    fun offlineProbeSuccessBecomesOnline() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
        var calls = 0
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 50L,
            check = {
                calls += 1
                ProfileCheckResult()
            }
        )
        assertTrue(ran)
        assertEquals(1, calls)
        assertEquals(ConnectionStatus.Session.Online, holder.status.value.session)
        assertEquals(50L, holder.status.value.lastUpdatedMs)
        assertNull(holder.status.value.lastFailure)
    }

    @Test
    fun onlineProbeUnreachableBecomesOffline() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 10L)
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 20L,
            check = {
                ProfileCheckResult(
                    hasError = true,
                    failure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.Connect
                    )
                )
            }
        )
        assertTrue(ran)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
        assertEquals(ConnectionStatus.Chip.Offline, holder.status.value.chip)
        assertEquals(10L, holder.status.value.lastUpdatedMs)
    }

    @Test
    fun onlineProbeAuthStopsFurtherProbes() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 10L)
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 20L,
            check = {
                ProfileCheckResult(hasError = true, failure = EnigmaFailure.Auth)
            }
        )
        assertTrue(ran)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
        assertEquals(EnigmaFailure.Auth, holder.status.value.lastFailure)
        assertFalse(shouldProbeReachability(holder.status.value))
    }

    @Test
    fun onlineProbeSuccessStaysOnline() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 10L)
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 30L,
            check = { ProfileCheckResult() }
        )
        assertTrue(ran)
        assertEquals(ConnectionStatus.Session.Online, holder.status.value.session)
        assertEquals(30L, holder.status.value.lastUpdatedMs)
    }

    @Test
    fun offlineProbeUnreachableStaysOffline() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Connect),
            hasCache = true
        )
        val ran = probeSessionReachabilityIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 60L,
            check = {
                ProfileCheckResult(
                    hasError = true,
                    failure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.Dns
                    )
                )
            }
        )
        assertTrue(ran)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
        assertEquals(ConnectionStatus.Chip.Offline, holder.status.value.chip)
    }
}
