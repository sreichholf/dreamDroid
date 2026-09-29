package net.reichholf.dreamdroid.ui.epg

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * EPG events of one day, under [day]; [day] is null for events without a start. The first
 * [earlierStarts] events started on an earlier day and are still running, so their rows
 * show the date, not only the time.
 */
data class EpgDaySection(val day: UiText?, val events: List<Event>, val earlierStarts: Int = 0)

/**
 * [events] ordered by start and grouped by the day they start in [zone]. An event that
 * started before [today] and is still running at [nowSec] counts as today. Today and
 * tomorrow are named; other days show weekday and date in [locale].
 */
internal fun epgDaySections(
    events: List<Event>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowSec: Long = System.currentTimeMillis() / 1000L
): List<EpgDaySection> {
    if (events.isEmpty()) {
        return emptyList()
    }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", locale)
    fun startDay(event: Event): LocalDate? = event.start.trim().toLongOrNull()?.let {
        Instant.ofEpochSecond(it).atZone(zone).toLocalDate()
    }
    fun running(event: Event): Boolean {
        val start = event.start.trim().toLongOrNull() ?: return false
        return start + (event.duration.trim().toLongOrNull() ?: 0L) > nowSec
    }
    return events
        .sortedBy { it.start.trim().toLongOrNull() ?: Long.MAX_VALUE }
        .groupBy { event ->
            startDay(event)?.let { if (it < today && running(event)) today else it }
        }
        .map { (day, dayEvents) ->
            val header = when (day) {
                null -> null
                today -> UiText.Resource(R.string.today)
                today.plusDays(1) -> UiText.Resource(R.string.tomorrow)
                else -> UiText.Raw("${weekdayFormat.format(day)}, ${dateFormat.format(day)}")
            }
            val earlier = if (day == null) 0 else dayEvents.count { (startDay(it) ?: day) < day }
            EpgDaySection(header, dayEvents, earlier)
        }
}
