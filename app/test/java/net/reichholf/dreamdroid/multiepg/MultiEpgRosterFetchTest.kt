package net.reichholf.dreamdroid.multiepg

import java.net.UnknownHostException
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.EnigmaFailureException
import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun failedGetservicesUsesFormatErrorForEnigmaFailure() {
        val previous = playableMultiEpgRoster(
            listOf(Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste"))
        )
        val applied = applyBouquetRoster(
            previous,
            MultiEpgRosterFetch(error = EnigmaFailureException(EnigmaFailure.Auth)),
            formatError = { "authorization failed" }
        )
        assertSame(previous, applied.roster)
        assertEquals("authorization failed", applied.errorMessage)
    }

    @Test
    fun unreachableGetservicesDoesNotSetErrorMessage() {
        val previous = playableMultiEpgRoster(
            listOf(Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste"))
        )
        val applied = applyBouquetRoster(
            previous,
            MultiEpgRosterFetch(
                error = EnigmaFailureException(
                    EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
                )
            ),
            formatError = { "host_not_found" }
        )
        assertSame(previous, applied.roster)
        assertEquals(null, applied.errorMessage)
    }

    @Test
    fun rawUnknownHostDoesNotSetErrorMessage() {
        val previous = playableMultiEpgRoster(
            listOf(Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste"))
        )
        val applied = applyBouquetRoster(
            previous,
            MultiEpgRosterFetch(error = UnknownHostException("box.local")),
            formatError = { "host_not_found" }
        )
        assertSame(previous, applied.roster)
        assertEquals(null, applied.errorMessage)
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
