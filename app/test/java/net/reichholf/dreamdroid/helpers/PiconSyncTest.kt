package net.reichholf.dreamdroid.helpers

import androidx.work.WorkInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiconSyncTest {
    @Test
    fun inProgressWhenEnqueuedRunningOrBlocked() {
        assertTrue(PiconSync.isWorkInProgress(listOf(WorkInfo.State.ENQUEUED)))
        assertTrue(PiconSync.isWorkInProgress(listOf(WorkInfo.State.RUNNING)))
        assertTrue(PiconSync.isWorkInProgress(listOf(WorkInfo.State.BLOCKED)))
        assertTrue(
            PiconSync.isWorkInProgress(
                listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.RUNNING)
            )
        )
    }

    @Test
    fun finishedStatesAreNotInProgress() {
        assertFalse(PiconSync.isWorkInProgress(emptyList()))
        assertFalse(PiconSync.isWorkInProgress(listOf(WorkInfo.State.SUCCEEDED)))
        assertFalse(PiconSync.isWorkInProgress(listOf(WorkInfo.State.FAILED)))
        assertFalse(PiconSync.isWorkInProgress(listOf(WorkInfo.State.CANCELLED)))
    }
}
