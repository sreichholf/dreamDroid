package net.reichholf.dreamdroid.ui.session

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReachabilityTest {
    @Test
    fun onlyOfflineIdleIsProbed() {
        assertFalse(shouldProbeReachability(ConnectionStatus()))
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(session = ConnectionStatus.Session.Online)
            )
        )
        assertFalse(
            shouldProbeReachability(
                ConnectionStatus(
                    session = ConnectionStatus.Session.Offline,
                    checking = true
                )
            )
        )
        assertTrue(
            shouldProbeReachability(
                ConnectionStatus(session = ConnectionStatus.Session.Offline)
            )
        )
    }

    @Test
    fun probeSkipsOnlineWithoutCallingCheck() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 1L)
        var calls = 0
        val recovered = probeOfflineSessionIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 2L,
            check = {
                calls += 1
                ProfileCheckResult()
            }
        )
        assertFalse(recovered)
        assertEquals(0, calls)
        assertEquals(ConnectionStatus.Session.Online, holder.status.value.session)
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
            probeOfflineSessionIfNeeded(
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
            probeOfflineSessionIfNeeded(
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
        val recovered = probeOfflineSessionIfNeeded(
            holder = holder,
            hasCache = true,
            nowMs = 50L,
            check = {
                calls += 1
                ProfileCheckResult()
            }
        )
        assertTrue(recovered)
        assertEquals(1, calls)
        assertEquals(ConnectionStatus.Session.Online, holder.status.value.session)
        assertEquals(50L, holder.status.value.lastUpdatedMs)
    }

    @Test
    fun offlineProbeUnreachableStaysOffline() = runBlocking {
        val holder = SessionConnectionHolder()
        holder.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Connect),
            hasCache = true
        )
        val recovered = probeOfflineSessionIfNeeded(
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
        assertFalse(recovered)
        assertEquals(ConnectionStatus.Session.Offline, holder.status.value.session)
        assertEquals(ConnectionStatus.Chip.Offline, holder.status.value.chip)
    }
}
