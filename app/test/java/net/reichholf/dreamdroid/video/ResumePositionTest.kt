package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [resumePositionMs] keeps a recording's position and leaves a live stream at the edge. */
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
}
