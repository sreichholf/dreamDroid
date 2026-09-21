package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvHubBouquetSpacerTest {
    private val channel = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
    private val marker = "1:64:1:0:0:0:0:0:0:0:"
    private val spacer = "1:832:D:0:0:0:0:0:0:0:"

    @Test
    fun spacerIsExactlyTheFlagsField832() {
        assertTrue(Service.isSpacer(spacer))
        assertTrue(Service.isMarker(spacer))
        assertTrue(Service.isSpacer("4097:832:1:0:0:0:0:0:0:0:"))
        assertFalse(Service.isSpacer(marker))
        assertTrue(Service.isMarker(marker))
        assertFalse(Service.isSpacer(channel))
        assertFalse(Service.isMarker(channel))
        assertFalse(Service.isSpacer(null))
        assertFalse(Service.isSpacer(""))
        assertFalse(Service.isSpacer("1"))
        assertFalse(Service.isSpacer("1:64:832:0:0:0:0:0:0:0:"))
        assertFalse(Service.isSpacer("1:833:D:0:0:0:0:0:0:0:"))
    }

    @Test
    fun hubRowDropsSpacersAndKeepsDescriptionMarkers() {
        val channelRow = ServiceNowNext(serviceReference = channel, serviceName = "Das Erste HD")
        val markerRow = ServiceNowNext(serviceReference = marker, serviceName = "News")
        val spacerRow = ServiceNowNext(serviceReference = spacer, serviceName = "")
        val rows = withoutBouquetSpacers(listOf(channelRow, spacerRow, markerRow, spacerRow))
        assertEquals(listOf(channelRow, markerRow), rows)
        assertEquals(emptyList<ServiceNowNext>(), withoutBouquetSpacers(emptyList()))
    }
}
