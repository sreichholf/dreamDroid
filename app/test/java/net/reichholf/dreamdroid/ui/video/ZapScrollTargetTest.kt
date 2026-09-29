package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.ui.services.ServiceListItem
import net.reichholf.dreamdroid.ui.services.ServiceRowKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZapScrollTargetTest {
    @Test
    fun withoutSectionHeadersTheRowItselfIsTheTarget() {
        val items = listOf(channel(0), channel(1), channel(2))

        assertEquals(2, zapScrollTarget(items, 2))
    }

    @Test
    fun underASectionHeaderTheRowBeforeGoesUnderIt() {
        val items = listOf(marker(0, "Doku"), channel(1), channel(2), channel(3))

        assertEquals(0, zapScrollTarget(items, 1))
        assertEquals(2, zapScrollTarget(items, 3))
    }

    @Test
    fun spacersAreSkipped() {
        val items = listOf(marker(0, "Doku"), channel(1), spacer(2), channel(3))

        assertEquals(1, zapScrollTarget(items, 3))
    }

    @Test
    fun aSpacerAloneIsNoSectionHeader() {
        val items = listOf(spacer(0), channel(1))

        assertEquals(1, zapScrollTarget(items, 1))
    }

    private fun channel(index: Int) = ServiceListItem(
        index,
        "1:0:1:$index:1:1:1:0:0:0:",
        "Channel $index",
        ServiceRowKind.CHANNEL
    )

    private fun marker(index: Int, name: String) =
        ServiceListItem(index, "1:64:$index:0:0:0:0:0:0:0::$name", name, ServiceRowKind.MARKER)

    private fun spacer(index: Int) =
        ServiceListItem(index, "1:832:$index:0:0:0:0:0:0:0:", "", ServiceRowKind.MARKER)
}
