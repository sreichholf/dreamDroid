package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposeActivityResultPendingTest {
    @Test
    fun attachedListenerDoesNotHoldTheResult() {
        val incoming = PendingComposeActivityResult(requestCode = 1, resultCode = -1)
        assertNull(holdComposeActivityResultIfDetached(listenerAttached = true, incoming))
    }

    @Test
    fun detachedListenerHoldsTheResult() {
        val incoming = PendingComposeActivityResult(requestCode = 5, resultCode = -1)
        assertEquals(
            incoming,
            holdComposeActivityResultIfDetached(listenerAttached = false, incoming)
        )
    }

    @Test
    fun takeDeliversOnlyWhenAListenerIsAttached() {
        val pending = PendingComposeActivityResult(requestCode = 5, resultCode = -1)
        assertEquals(
            pending,
            takePendingComposeActivityResult(listenerAttached = true, pending)
        )
        assertNull(takePendingComposeActivityResult(listenerAttached = false, pending))
        assertNull(takePendingComposeActivityResult(listenerAttached = true, pending = null))
    }
}
