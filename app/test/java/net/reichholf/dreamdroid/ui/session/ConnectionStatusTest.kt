package net.reichholf.dreamdroid.ui.session

import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionStatusTest {
    @Test
    fun progressIsCheckingChipAndDoesNotStoreASession() {
        val holder = SessionConnectionHolder()
        holder.beginChecking()
        val status = holder.status.value
        assertTrue(status.checking)
        assertNull(status.session)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
        assertEquals(R.string.session_checking, status.chipLabelRes())
    }

    @Test
    fun checkSuccessIsOnlineWithLastUpdated() {
        val holder = SessionConnectionHolder()
        holder.beginChecking()
        holder.onSuccess(nowMs = 1_700_000_000_000L)
        val status = holder.status.value
        assertFalse(status.checking)
        assertEquals(ConnectionStatus.Session.Online, status.session)
        assertEquals(1_700_000_000_000L, status.lastUpdatedMs)
        assertNull(status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Online, status.chip)
        assertEquals(R.string.session_online, status.chipLabelRes())
    }

    @Test
    fun checkingOverlayKeepsStoredOnlineSession() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 10L)
        holder.beginChecking()
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Online, status.session)
        assertEquals(10L, status.lastUpdatedMs)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
    }

    @Test
    fun unreachableWithoutCacheDoesNotClaimOffline() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 20L)
        holder.beginChecking()
        val failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout)
        holder.onFailure(failure, hasCache = false)
        val status = holder.status.value
        assertFalse(status.checking)
        assertNull(status.session)
        assertEquals(20L, status.lastUpdatedMs)
        assertEquals(failure, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
    }

    @Test
    fun unreachableWithCacheIsOfflineAndKeepsLastUpdated() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 40L)
        val failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
        holder.onFailure(failure, hasCache = true)
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Offline, status.session)
        assertEquals(40L, status.lastUpdatedMs)
        assertEquals(failure, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Offline, status.chip)
        assertEquals(R.string.session_offline, status.chipLabelRes())
    }

    @Test
    fun authWithoutCacheStoresKindAndDoesNotLabelChipAuth() {
        val holder = SessionConnectionHolder()
        holder.onFailure(EnigmaFailure.Auth, hasCache = false)
        val status = holder.status.value
        assertNull(status.session)
        assertEquals(EnigmaFailure.Auth, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
        assertEquals(R.string.session_checking, status.chipLabelRes())
    }

    @Test
    fun authWithCacheIsOfflineWithAuthKindNotAnAuthChip() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 60L)
        holder.onFailure(EnigmaFailure.Auth, hasCache = true)
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Offline, status.session)
        assertEquals(EnigmaFailure.Auth, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Offline, status.chip)
        assertEquals(R.string.session_offline, status.chipLabelRes())
    }

    @Test
    fun illegalHostNeverGoesOfflineEvenWithCache() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 70L)
        val failure =
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.IllegalHost)
        holder.onFailure(failure, hasCache = true)
        val status = holder.status.value
        assertNull(status.session)
        assertEquals(failure, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
    }

    @Test
    fun httpFailureDoesNotClaimOffline() {
        val holder = SessionConnectionHolder()
        holder.onFailure(EnigmaFailure.Http(500, "Server Error"), hasCache = true)
        val status = holder.status.value
        assertNull(status.session)
        assertTrue(status.lastFailure is EnigmaFailure.Http)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
    }

    @Test
    fun cancelledFailureLeavesStoredSession() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 80L)
        holder.beginChecking()
        holder.onFailure(EnigmaFailure.Cancelled, hasCache = false)
        val status = holder.status.value
        assertFalse(status.checking)
        assertEquals(ConnectionStatus.Session.Online, status.session)
        assertEquals(80L, status.lastUpdatedMs)
        assertNull(status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Online, status.chip)
    }

    @Test
    fun successAfterOfflineClearsFailureAndIsOnline() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 90L)
        holder.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Connect),
            hasCache = true
        )
        holder.onSuccess(nowMs = 100L)
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Online, status.session)
        assertEquals(100L, status.lastUpdatedMs)
        assertNull(status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Online, status.chip)
    }

    @Test
    fun profileCheckHardErrorUsesFailureKind() {
        val holder = SessionConnectionHolder()
        val failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Ssl)
        holder.applyProfileCheckResult(
            ProfileCheckResult(hasError = true, failure = failure),
            hasCache = false,
            nowMs = 110L
        )
        assertSame(failure, holder.status.value.lastFailure)
        assertNull(holder.status.value.session)
    }

    @Test
    fun profileCheckUnreachableWithCacheIsOffline() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 115L)
        val failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Connect)
        holder.applyProfileCheckResult(
            ProfileCheckResult(hasError = true, failure = failure),
            hasCache = true,
            nowMs = 116L
        )
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Offline, status.session)
        assertEquals(115L, status.lastUpdatedMs)
        assertSame(failure, status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Offline, status.chip)
    }

    @Test
    fun profileCheckSoftErrorIsOnline() {
        val holder = SessionConnectionHolder()
        holder.applyProfileCheckResult(
            ProfileCheckResult(hasError = true, isSoftError = true),
            hasCache = false,
            nowMs = 120L
        )
        val status = holder.status.value
        assertEquals(ConnectionStatus.Session.Online, status.session)
        assertEquals(120L, status.lastUpdatedMs)
        assertNull(status.lastFailure)
    }

    @Test
    fun profileChangeClearsPreviousSession() {
        val holder = SessionConnectionHolder()
        holder.onSuccess(nowMs = 130L)
        holder.resetForProfileChange()
        val status = holder.status.value
        assertTrue(status.checking)
        assertNull(status.session)
        assertNull(status.lastUpdatedMs)
        assertNull(status.lastFailure)
        assertEquals(ConnectionStatus.Chip.Checking, status.chip)
    }

    @Test
    fun hasUseDrivenCacheGainsTabStripMovieAndTimerSources() {
        assertFalse(hasUseDrivenCache(emptyList()))
        assertFalse(hasUseDrivenCache(Profile().apply { id = 1 }))
        assertTrue(hasUseDrivenCache(listOf("1:7:1:FROM BOUQUET \"userbouquet.fav.tv\"")))
        assertTrue(hasUseDrivenCache(emptyList(), hasMovieLocationStrip = true))
        assertTrue(hasUseDrivenCache(emptyList(), hasTimerSnapshot = true))
        assertFalse(
            hasUseDrivenCache(
                emptyList(),
                hasMovieLocationStrip = false,
                hasTimerSnapshot = false
            )
        )
    }

    @Test
    fun blocksMutationsUnlessSessionIsOnline() {
        assertTrue(ConnectionStatus().blocksMutations)
        assertTrue(
            ConnectionStatus(
                session = ConnectionStatus.Session.Offline,
                checking = false
            ).blocksMutations
        )
        assertFalse(
            ConnectionStatus(
                session = ConnectionStatus.Session.Online,
                checking = true
            ).blocksMutations
        )
        assertFalse(
            ConnectionStatus(session = ConnectionStatus.Session.Online).blocksMutations
        )
    }

    @Test
    fun skipReceiverHttpOnlyWhenOfflineAndCacheExists() {
        val offline = ConnectionStatus(session = ConnectionStatus.Session.Offline)
        val online = ConnectionStatus(session = ConnectionStatus.Session.Online)
        val checking = ConnectionStatus(checking = true)
        assertTrue(offline.shouldSkipReceiverHttp(hasCache = true))
        assertFalse(offline.shouldSkipReceiverHttp(hasCache = false))
        assertFalse(online.shouldSkipReceiverHttp(hasCache = true))
        assertFalse(checking.shouldSkipReceiverHttp(hasCache = true))
        assertFalse(shouldWaitForDeviceInfo(hasCache = true))
        assertTrue(shouldWaitForDeviceInfo(hasCache = false))
    }

    @Test
    fun profileCheckCheckingUiSkipsWhenCacheExists() {
        assertTrue(shouldShowProfileCheckCheckingUi(hasCache = false))
        assertFalse(shouldShowProfileCheckCheckingUi(hasCache = true))
    }

    @Test
    fun profileCheckFailedUiSkipsUnreachableWithCache() {
        val unreachable = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout)
        assertTrue(shouldShowProfileCheckFailedUi(hasCache = false, failure = unreachable))
        assertFalse(shouldShowProfileCheckFailedUi(hasCache = true, failure = unreachable))
        assertFalse(shouldShowProfileCheckFailedUi(hasCache = true, failure = EnigmaFailure.Auth))
        assertTrue(
            shouldShowProfileCheckFailedUi(
                hasCache = true,
                failure = EnigmaFailure.Http(500, "Server Error")
            )
        )
    }
}
