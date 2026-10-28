package net.reichholf.dreamdroid.ui.epg

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpgSearchSortTest {
    private val today = LocalDate.of(2030, 1, 1)
    private val midnight = today.atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    @Test
    fun dateSortKeepsDaySections() {
        val sections = sections(
            listOf(
                event("Later", "ZDF HD", midnight + DAY),
                event("Evening", "Das Erste HD", midnight + 20 * HOUR),
                event("Morning", "Das Erste HD", midnight + 8 * HOUR)
            ),
            EpgSearchSort.Date
        )

        assertEquals(
            listOf(UiText.Resource(R.string.today), UiText.Resource(R.string.tomorrow)),
            sections.map { it.day }
        )
        assertEquals(
            listOf(listOf("Morning", "Evening"), listOf("Later")),
            sections.map { section -> section.events.map { it.title } }
        )
    }

    @Test
    fun channelSortIsFlatAndAlphabetical() {
        val sections = sections(
            listOf(
                event("Wetter", "ZDF HD", midnight + 10),
                event("Tatort", "Das Erste HD", midnight + 20),
                event("Tagesschau", "Das Erste HD", midnight + 5)
            ),
            EpgSearchSort.Channel
        )

        assertNull(sections.single().day)
        assertEquals(
            listOf("Tagesschau", "Tatort", "Wetter"),
            sections.single().events.map { it.title }
        )
    }

    @Test
    fun titleSortIsCaseInsensitiveAndFlat() {
        val sections = sections(
            listOf(
                event("Zeta", "ZDF HD", midnight + 10),
                event("alpha", "Das Erste HD", midnight + 20),
                event("Beta", "ZDF HD", midnight + 5)
            ),
            EpgSearchSort.Title
        )

        assertNull(sections.single().day)
        assertEquals(
            listOf("alpha", "Beta", "Zeta"),
            sections.single().events.map { it.title }
        )
    }

    @Test
    fun channelSortPutsEventsWithoutStartLast() {
        val sections = sections(
            listOf(
                event("Unknown", "Das Erste HD", null),
                event("Morning", "Das Erste HD", midnight + 8 * HOUR)
            ),
            EpgSearchSort.Channel
        )

        assertEquals(
            listOf("Morning", "Unknown"),
            sections.single().events.map { it.title }
        )
    }

    @Test
    fun emptyEventsHaveNoSections() {
        EpgSearchSort.entries.forEach { sort ->
            assertEquals(emptyList<EpgDaySection>(), sections(emptyList(), sort))
        }
    }

    private fun sections(events: List<Event>, sort: EpgSearchSort): List<EpgDaySection> =
        epgSearchSections(events, sort, today, ZoneOffset.UTC, Locale.US, midnight)

    private fun event(title: String, serviceName: String, start: Long?) = Event(
        eventId = title,
        title = title,
        start = start?.toString().orEmpty(),
        duration = HOUR.toString(),
        serviceName = serviceName
    )

    private companion object {
        const val HOUR = 3_600L
        const val DAY = 24 * HOUR
    }
}
