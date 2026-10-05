package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvSessionTest {
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
        assertEquals(
            TvSessionGate.Checking(UiText.Resource(R.string.checking_connection)),
            tvSessionGate(status = checking, hasCache = false, receiverLabel = "u@box:80")
        )
        assertEquals(
            TvSessionGate.None,
            tvSessionGate(status = checking, hasCache = true, receiverLabel = "u@box:80")
        )
        val failed = ConnectionStatus(
            lastFailure = EnigmaFailure.Unreachable(
                EnigmaFailure.UnreachableReason.Connect
            )
        )
        assertEquals(
            TvSessionGate.Failed(
                UiText.Raw("u@box:80"),
                UiText.Resource(R.string.host_unreach)
            ),
            tvSessionGate(status = failed, hasCache = false, receiverLabel = "u@box:80")
        )
        assertEquals(
            TvSessionGate.None,
            tvSessionGate(status = failed, hasCache = true, receiverLabel = "u@box:80")
        )
    }

    @Test
    fun aFailureWithoutItsOwnMessageShowsTheConnectionError() {
        val cancelled = ConnectionStatus(lastFailure = EnigmaFailure.Cancelled)
        assertEquals(
            TvSessionGate.Failed(
                UiText.Raw("u@box:80"),
                UiText.Resource(R.string.connection_error)
            ),
            tvSessionGate(status = cancelled, hasCache = false, receiverLabel = "u@box:80")
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
            UiText.Raw("err"),
            unavailableTvHubMessage(
                usedCache = false,
                paintedRows = false,
                errorText = UiText.Raw("err")
            )
        )
        assertEquals(
            null,
            unavailableTvHubMessage(
                usedCache = true,
                paintedRows = false,
                errorText = UiText.Raw("err")
            )
        )
        assertEquals(
            null,
            unavailableTvHubMessage(
                usedCache = false,
                paintedRows = true,
                errorText = UiText.Raw("err")
            )
        )
    }
}
