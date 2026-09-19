package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvSessionTest {
    @Test
    fun hubHttpSkippedWhenCacheExistsAndNotOnline() {
        val cache = true
        assertTrue(
            shouldSkipTvHubHttp(ConnectionStatus(checking = true), cache)
        )
        assertTrue(
            shouldSkipTvHubHttp(
                ConnectionStatus(session = ConnectionStatus.Session.Offline),
                cache
            )
        )
        assertFalse(
            shouldSkipTvHubHttp(
                ConnectionStatus(session = ConnectionStatus.Session.Online),
                cache
            )
        )
        assertFalse(shouldSkipTvHubHttp(ConnectionStatus(checking = true), false))
    }

    @Test
    fun streamingOnlyWhenOnline() {
        assertFalse(ConnectionStatus().allowsStreaming())
        assertFalse(
            ConnectionStatus(checking = true).allowsStreaming()
        )
        assertFalse(
            ConnectionStatus(
                session = ConnectionStatus.Session.Offline
            ).allowsStreaming()
        )
        assertTrue(
            ConnectionStatus(
                session = ConnectionStatus.Session.Online
            ).allowsStreaming()
        )
        assertTrue(
            ConnectionStatus(
                session = ConnectionStatus.Session.Online,
                checking = true
            ).allowsStreaming()
        )
    }

    @Test
    fun tvStreamingActivityFinishesBeforePlaybackWhenOffline() {
        val offline = ConnectionStatus(session = ConnectionStatus.Session.Offline)
        assertFalse(
            shouldKeepTvStreamingActivity(
                isTelevision = true,
                status = offline,
                playbackAlreadyStarted = false
            )
        )
        assertTrue(
            shouldKeepTvStreamingActivity(
                isTelevision = true,
                status = offline,
                playbackAlreadyStarted = true
            )
        )
        assertTrue(
            shouldKeepTvStreamingActivity(
                isTelevision = false,
                status = offline,
                playbackAlreadyStarted = false
            )
        )
    }

    @Test
    fun recheckWhenOfflineOrFailedNotWhileChecking() {
        assertFalse(shouldShowTvSessionRecheck(ConnectionStatus(checking = true)))
        assertTrue(
            shouldShowTvSessionRecheck(
                ConnectionStatus(session = ConnectionStatus.Session.Offline)
            )
        )
        assertTrue(
            shouldShowTvSessionRecheck(
                ConnectionStatus(
                    lastFailure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.Timeout
                    )
                )
            )
        )
        assertFalse(
            shouldShowTvSessionRecheck(
                ConnectionStatus(session = ConnectionStatus.Session.Online)
            )
        )
    }

    @Test
    fun browseErrorHiddenOnSettingsAndWhenContentPainted() {
        assertFalse(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = false
            )
        )
        assertFalse(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_TIMERS_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = false
            )
        )
        assertTrue(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = false
            )
        )
        assertFalse(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = true
            )
        )
        assertFalse(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = true,
                errorText = "box offline",
                hasPaintedContent = false
            )
        )
        assertTrue(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_MULTIEPG_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = false
            )
        )
        assertFalse(
            shouldShowTvBrowseError(
                selectedHeaderId = TvComposeHubHost.HEADER_MULTIEPG_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = true
            )
        )
    }

    @Test
    fun profileCheckGateSkippedWhenCacheExists() {
        val checking = ConnectionStatus(checking = true)
        assertTrue(
            tvSessionGate(
                status = checking,
                hasCache = false,
                checkingMessage = "Checking",
                failedTitle = "t",
                failedMessage = "m"
            ) is TvSessionGate.Checking
        )
        assertEquals(
            TvSessionGate.None,
            tvSessionGate(
                status = checking,
                hasCache = true,
                checkingMessage = "Checking",
                failedTitle = "t",
                failedMessage = "m"
            )
        )
        val failed = ConnectionStatus(
            lastFailure = EnigmaFailure.Unreachable(
                EnigmaFailure.UnreachableReason.Connect
            )
        )
        assertTrue(
            tvSessionGate(
                status = failed,
                hasCache = false,
                checkingMessage = "Checking",
                failedTitle = "t",
                failedMessage = "m"
            ) is TvSessionGate.Failed
        )
        assertEquals(
            TvSessionGate.None,
            tvSessionGate(
                status = failed,
                hasCache = true,
                checkingMessage = "Checking",
                failedTitle = "t",
                failedMessage = "m"
            )
        )
    }

    @Test
    fun movieHeadersIgnoreHttpFallback() {
        assertEquals(
            emptyList<String>(),
            movieHeadersForTvHub(
                locationsFromReceiver = false,
                liveLocations = listOf("/hdd/movie"),
                cachedLocations = null
            )
        )
        assertEquals(
            listOf("/media/hdd/movie"),
            movieHeadersForTvHub(
                locationsFromReceiver = false,
                liveLocations = listOf("/hdd/movie"),
                cachedLocations = listOf("/media/hdd/movie")
            )
        )
        assertEquals(
            listOf("/hdd/movie", "/media/usb"),
            movieHeadersForTvHub(
                locationsFromReceiver = true,
                liveLocations = listOf("/hdd/movie", "/media/usb"),
                cachedLocations = listOf("/old")
            )
        )
    }

    @Test
    fun unavailableMessageOnlyWithoutPaintedRows() {
        assertEquals(
            "err",
            unavailableTvHubMessage(
                usedCache = false,
                paintedRows = false,
                errorText = "err"
            )
        )
        assertEquals(
            null,
            unavailableTvHubMessage(
                usedCache = true,
                paintedRows = false,
                errorText = "err"
            )
        )
        assertEquals(
            null,
            unavailableTvHubMessage(
                usedCache = false,
                paintedRows = true,
                errorText = "err"
            )
        )
    }
}
