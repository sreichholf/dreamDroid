package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [resumePositionMs] keeps a recording's position and leaves a live stream at the edge; a
 * stream that played to its end starts over. [playbackPositionMs] keeps a resume that has not
 * played a frame yet.
 */
class ResumePositionTest {
    @Test
    fun aRecordingResumesWhereItWas() {
        assertEquals(1_200_000L, resumePositionMs(live = false, timeMs = 1_200_000L))
    }

    @Test
    fun aLiveStreamStartsAtTheLiveEdge() {
        assertEquals(0L, resumePositionMs(live = true, timeMs = 1_200_000L))
    }

    @Test
    fun aStreamThatNeverPlayedStartsAtTheBeginning() {
        assertEquals(0L, resumePositionMs(live = false, timeMs = -1L))
    }

    @Test
    fun aRecordingThatPlayedToItsEndStartsOver() {
        assertEquals(0L, resumePositionMs(live = false, timeMs = 1_200_000L, ended = true))
    }

    @Test
    fun aRecordingStoppedAtItsReportedEndStartsOver() {
        assertEquals(
            0L,
            resumePositionMs(live = false, timeMs = 1_200_000L, lengthMs = 1_200_000L)
        )
    }

    @Test
    fun aRecordingStoppedJustBeforeItsReportedEndStartsOver() {
        assertEquals(
            0L,
            resumePositionMs(live = false, timeMs = 1_199_500L, lengthMs = 1_200_000L)
        )
    }

    @Test
    fun aRecordingBeforeItsEndKeepsItsPosition() {
        assertEquals(
            1_198_000L,
            resumePositionMs(live = false, timeMs = 1_198_000L, lengthMs = 1_200_000L)
        )
    }

    @Test
    fun aStreamThatReportedATimeFollowsTheEngine() {
        assertEquals(1_200_000L, playbackPositionMs(timeMs = 1_200_000L, requestedMs = 600_000L))
    }

    @Test
    fun aResumeBeforeTheFirstFrameKeepsItsRequestedPosition() {
        assertEquals(600_000L, playbackPositionMs(timeMs = 0L, requestedMs = 600_000L))
    }

    @Test
    fun aNegativeTimeFallsBackToTheRequestedPosition() {
        assertEquals(600_000L, playbackPositionMs(timeMs = -1L, requestedMs = 600_000L))
    }
}
