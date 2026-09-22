package net.reichholf.dreamdroid.ui.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpgSearchDraftTest {
    @Test
    fun queryChangeResetsDraftToRouteQuery() {
        val draft = epgSearchDraftForRoute(
            draft = "tagesschau extra",
            previousQuery = "tagesschau",
            previousEpoch = 1,
            query = "heute",
            remountEpoch = 1
        )
        assertEquals("heute", draft)
    }

    @Test
    fun remountEpochChangeResetsDraftToRouteQuery() {
        val draft = epgSearchDraftForRoute(
            draft = "tagesschau extra",
            previousQuery = "tagesschau",
            previousEpoch = 1,
            query = "tagesschau",
            remountEpoch = 2
        )
        assertEquals("tagesschau", draft)
    }

    @Test
    fun unchangedQueryAndEpochKeepsDraft() {
        val draft = epgSearchDraftForRoute(
            draft = "tagesschau extra",
            previousQuery = "tagesschau",
            previousEpoch = 1,
            query = "tagesschau",
            remountEpoch = 1
        )
        assertEquals("tagesschau extra", draft)
    }
}
