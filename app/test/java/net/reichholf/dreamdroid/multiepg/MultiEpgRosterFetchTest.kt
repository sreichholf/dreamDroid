package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEpgRosterFetchTest {
    @Test
    fun failedGetservicesKeepsLastGoodRoster() {
        val previous = playableMultiEpgRoster(
            listOf(
                Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste"),
                Service("1:0:1:2:1:1:0:0:0:0:", "ZDF")
            )
        )
        val applied = applyBouquetRoster(
            previous,
            MultiEpgRosterFetch(error = IllegalStateException("getservices down"))
        )
        assertSame(previous, applied.roster)
        assertEquals("getservices down", applied.errorMessage)
    }

    @Test
    fun successfulEmptyRosterReplacesPrevious() {
        val previous = listOf(Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste"))
        val applied = applyBouquetRoster(previous, MultiEpgRosterFetch(emptyList()))
        assertTrue(applied.roster.isEmpty())
        assertEquals(null, applied.errorMessage)
    }

    @Test
    fun successfulFetchReplacesWithPlayableMembers() {
        val previous = listOf(Service("1:0:1:9:1:1:0:0:0:0:", "Old"))
        val applied = applyBouquetRoster(
            previous,
            MultiEpgRosterFetch(
                listOf(
                    Service("1:64:0:0:0:0:0:0:0:0:", "---"),
                    Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste")
                )
            )
        )
        assertEquals(1, applied.roster.size)
        assertEquals("Das Erste", applied.roster[0].name)
        assertEquals(null, applied.errorMessage)
    }
}
