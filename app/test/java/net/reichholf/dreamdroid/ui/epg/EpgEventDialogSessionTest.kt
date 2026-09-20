package net.reichholf.dreamdroid.ui.epg

import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpgEventDialogSessionTest {
    @Test
    fun dismissProgressCancelsSetTimerJob() {
        val session = EpgEventDialogSession()
        val job = Job()
        session.trackSetTimerJob(job)
        session.dismissProgress()
        assertTrue(job.isCancelled)
        assertNull(session.progress)
    }

    @Test
    fun trackSetTimerJobCancelsThePreviousJob() {
        val session = EpgEventDialogSession()
        val first = Job()
        val second = Job()
        session.trackSetTimerJob(first)
        session.trackSetTimerJob(second)
        assertTrue(first.isCancelled)
        assertTrue(second.isActive)
        session.dismissProgress()
        assertTrue(second.isCancelled)
    }
}
