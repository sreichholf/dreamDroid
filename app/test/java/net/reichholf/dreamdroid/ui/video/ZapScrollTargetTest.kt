package net.reichholf.dreamdroid.ui.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ZapScrollTargetTest {
    private val refs = listOf(
        "1:64:0:0:0:0:0:0:0:0::Doku",
        "1:0:1:1:1:1:1:0:0:0:",
        "1:0:1:2:1:1:1:0:0:0:",
        "1:0:1:3:1:1:1:0:0:0:",
        "1:0:1:4:1:1:1:0:0:0:"
    )

    @Test
    fun rowsAboveThePlayingChannelStayInView() {
        assertEquals(2, zapScrollTarget(refs, "1:0:1:4:1:1:1:0:0:0:"))
    }

    @Test
    fun nearTheTopTheListStartsAtTheFirstRow() {
        assertEquals(0, zapScrollTarget(refs, "1:0:1:1:1:1:1:0:0:0:"))
    }

    @Test
    fun noTargetWithoutAPlayingChannelInTheList() {
        assertNull(zapScrollTarget(refs, "1:0:1:99:1:1:1:0:0:0:"))
        assertNull(zapScrollTarget(refs, null))
        assertNull(zapScrollTarget(refs, refs.first()))
    }
}
