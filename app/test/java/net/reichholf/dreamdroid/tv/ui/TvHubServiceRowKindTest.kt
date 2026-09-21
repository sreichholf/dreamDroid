package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.helpers.enigma2.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvHubServiceRowKindTest {
    @Test
    fun descriptionMarkerIsASectionHeader() {
        val ref = "1:64:1:0:0:0:0:0:0:0:"
        assertTrue(Service.isMarker(ref))
        assertFalse(Service.isSpacer(ref))
        assertTrue(tvHubDrawsMarkerHeader(ref))
        assertEquals(TvHubServiceRowKind.MARKER_HEADER, tvHubServiceRowKind(ref))
    }

    @Test
    fun channelStaysAServiceCard() {
        val ref = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
        assertFalse(Service.isMarker(ref))
        assertFalse(Service.isSpacer(ref))
        assertFalse(tvHubDrawsMarkerHeader(ref))
        assertEquals(TvHubServiceRowKind.CHANNEL, tvHubServiceRowKind(ref))
    }

    @Test
    fun spacerFlag832IsLeftAlone() {
        val ref = "1:832:1:0:0:0:0:0:0:0:"
        assertTrue(Service.isMarker(ref))
        assertTrue(Service.isSpacer(ref))
        assertFalse(tvHubDrawsMarkerHeader(ref))
        assertEquals(TvHubServiceRowKind.SPACER, tvHubServiceRowKind(ref))
        assertEquals(Service.ROSTER_KIND_MARKER, Service.rosterRowKind(ref))
    }
}
