package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.video.PlayerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayChromeTest {
    @Test
    fun liveWithEpgShowsTheEventsAndFollowsTheProgrammeNotTheStream() {
        val session = VideoPlaybackUiState(title = "zdf").zappedTo(
            ServiceNowNext(ZDF, "ZDF HD", now = NEWS, next = MOVIE_EVENT)
        )
        // The engine reports a live stream as seekable, with its own time.
        val player = PlayerState(seekable = true, lengthMs = 50_000, timeMs = 40_000)

        val chrome = overlayChrome(session, player, nowMs = (START_SEC + 600) * 1000)

        assertEquals("ZDF HD", chrome.title)
        assertFalse(chrome.recording)
        assertNull(chrome.subtitle)
        assertNull(chrome.remaining)
        assertTrue(chrome.showInfoButton)
        assertFalse(chrome.showPvrControls)
        assertFalse(chrome.seekable)
        assertEquals(OverlayRow("19:00", "heute", "30 min"), chrome.now)
        assertEquals(NEWS, chrome.nowEvent)
        assertEquals(OverlayRow("19:30", "Tatort", "90 min"), chrome.next)
        assertEquals(OverlayProgress(position = 600, max = 1800), chrome.progress)
    }

    @Test
    fun liveWithoutEpgKeepsAnEmptyNowRowAndNoBar() {
        val session = VideoPlaybackUiState(
            title = "Fallback",
            playing = VideoPlaying.Live(ServiceNowNext(ZDF, serviceName = ""))
        )

        val chrome = overlayChrome(session, PlayerState(lengthMs = 50_000), nowMs = 0)

        assertEquals("Fallback", chrome.title)
        assertTrue(chrome.showInfoButton)
        assertEquals(OverlayRow("", "", ""), chrome.now)
        assertNull(chrome.nowEvent)
        assertNull(chrome.next)
        assertNull(chrome.progress)
    }

    @Test
    fun liveHidesANextEventWithoutTitle() {
        val session = VideoPlaybackUiState().zappedTo(
            ServiceNowNext(ZDF, "ZDF HD", now = NEWS, next = Event(start = "1"))
        )

        assertNull(overlayChrome(session, PlayerState(), nowMs = 0).next)
    }

    @Test
    fun recordingWithEngineLengthShowsPositionLengthAndTimeLeft() {
        val session = recording(
            Movie(
                title = "Tatort",
                serviceName = "Das Erste",
                timeReadable = "So, 27.10. - 20:15",
                length = "1:00"
            )
        )
        val player = PlayerState(
            seekable = true,
            lengthMs = 120_000,
            timeMs = 30_000,
            position = 0.25f
        )

        val chrome = overlayChrome(session, player, nowMs = 0)

        assertEquals("Tatort", chrome.title)
        assertTrue(chrome.recording)
        assertEquals("Das Erste · So, 27.10. - 20:15", chrome.subtitle)
        assertEquals("\u221201:30", chrome.remaining)
        assertTrue(chrome.showInfoButton)
        assertTrue(chrome.showPvrControls)
        assertTrue(chrome.seekable)
        assertEquals(OverlayRow("00:30", "Das Erste", "02:00"), chrome.now)
        assertNull(chrome.nowEvent)
        assertNull(chrome.next)
        assertEquals(OverlayProgress(position = 30, max = 120), chrome.progress)
    }

    @Test
    fun recordingWithOnlyTheMovieLengthShowsTimesButNoBarUntilSeekable() {
        val session = recording(Movie(title = "", serviceName = "Arte", length = "90:00"))
        val player = PlayerState(position = 0.5f)

        val chrome = overlayChrome(session, player, nowMs = 0)

        assertEquals("Film", chrome.title)
        assertTrue(chrome.recording)
        assertEquals("Arte", chrome.subtitle)
        assertEquals("\u221245:00", chrome.remaining)
        assertEquals(OverlayRow("45:00", "Arte", "90:00"), chrome.now)
        assertNull(chrome.progress)
        assertEquals(
            OverlayProgress(position = 5_000, max = FAKE_PROGRESS_LENGTH_SEC),
            overlayChrome(session, player.copy(seekable = true), nowMs = 0).progress
        )
    }

    @Test
    fun recordingWithoutAnyLengthHidesTheNowRow() {
        val session = recording(Movie(title = "Tatort", length = "unknown"))

        val chrome = overlayChrome(session, PlayerState(), nowMs = 0)

        assertTrue(chrome.recording)
        assertNull(chrome.now)
        assertNull(chrome.subtitle)
        assertNull(chrome.remaining)
    }

    @Test
    fun seekableStreamWithoutInfoGetsPvrControlsAndTheEngineBar() {
        val session = VideoPlaybackUiState(title = "Stream")
        val player = PlayerState(seekable = true, lengthMs = 60_000, timeMs = 15_000)

        val chrome = overlayChrome(session, player, nowMs = 0)

        assertEquals("Stream", chrome.title)
        assertTrue(chrome.recording)
        assertFalse(chrome.showInfoButton)
        assertTrue(chrome.showPvrControls)
        assertTrue(chrome.seekable)
        assertNull(chrome.now)
        assertNull(chrome.next)
        assertEquals(OverlayProgress(position = 15, max = 60), chrome.progress)
    }

    @Test
    fun nothingPlayingShowsTheTitleAndNothingElse() {
        val chrome = overlayChrome(VideoPlaybackUiState(), PlayerState(), nowMs = 0)

        assertEquals(
            OverlayChrome(
                title = "",
                recording = false,
                subtitle = null,
                remaining = null,
                showInfoButton = false,
                showPvrControls = false,
                seekable = false,
                now = null,
                nowEvent = null,
                next = null,
                progress = null
            ),
            chrome
        )
    }

    private fun recording(movie: Movie): VideoPlaybackUiState =
        VideoPlaybackUiState().withExtras("Film", serviceRef = null, bouquetRef = null, movie)

    private companion object {
        const val ZDF = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val START_SEC = 1_700_000_000L

        val NEWS = Event(
            eventId = "1",
            title = "heute",
            start = START_SEC.toString(),
            duration = "1800",
            startTimeReadable = "19:00",
            durationReadable = "30 min"
        )
        val MOVIE_EVENT = Event(
            eventId = "2",
            title = "Tatort",
            start = (START_SEC + 1800).toString(),
            duration = "5400",
            startTimeReadable = "19:30",
            durationReadable = "90 min"
        )
    }
}
