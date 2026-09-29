package net.reichholf.dreamdroid.ui.epg

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpgDaySectionsTest {
    private val today = LocalDate.of(2030, 1, 1)
    private val midnight = today.atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    @Test
    fun ordersByStartAndNamesTodayAndTomorrow() {
        val sections = sections(
            event("Later", midnight + 3 * DAY + 60),
            event("Tomorrow", midnight + DAY + 60),
            event("Evening", midnight + 20 * HOUR),
            event("Morning", midnight + 8 * HOUR)
        )

        assertEquals(
            listOf(
                UiText.Resource(R.string.today),
                UiText.Resource(R.string.tomorrow),
                UiText.Raw("Friday, Jan 4, 2030")
            ),
            sections.map { it.day }
        )
        assertEquals(
            listOf(listOf("Morning", "Evening"), listOf("Tomorrow"), listOf("Later")),
            sections.map { section -> section.events.map { it.title } }
        )
    }

    @Test
    fun runningEventFromYesterdayCountsAsToday() {
        val sections = sections(
            event("Late movie", midnight - HOUR),
            event("Morning", midnight + 8 * HOUR)
        )

        assertEquals(listOf(UiText.Resource(R.string.today)), sections.map { it.day })
        assertEquals(listOf("Late movie", "Morning"), sections.single().events.map { it.title })
    }

    @Test
    fun eventsWithoutStartGoLastWithoutHeader() {
        val sections = sections(event("Unknown", null), event("Morning", midnight + 8 * HOUR))

        assertEquals(listOf(UiText.Resource(R.string.today), null), sections.map { it.day })
        assertEquals(listOf("Unknown"), sections.last().events.map { it.title })
    }

    private fun sections(vararg events: Event) =
        epgDaySections(events.toList(), today, ZoneOffset.UTC, Locale.US)

    private fun event(title: String, start: Long?) =
        Event(eventId = title, title = title, start = start?.toString().orEmpty())

    private companion object {
        const val HOUR = 3_600L
        const val DAY = 24 * HOUR
    }
}
