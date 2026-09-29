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

/** Search results of one day, under [day]; [day] is null for events without a start. */
data class EpgSearchSection(val day: UiText?, val events: List<Event>)

/**
 * [events] ordered by start and grouped by the day they start in [zone]. Today and
 * tomorrow are named; other days show weekday and date in [locale].
 */
internal fun epgSearchSections(
    events: List<Event>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): List<EpgSearchSection> {
    if (events.isEmpty()) {
        return emptyList()
    }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", locale)
    return events
        .sortedBy { it.start.trim().toLongOrNull() ?: Long.MAX_VALUE }
        .groupBy { event ->
            event.start.trim().toLongOrNull()?.let {
                Instant.ofEpochSecond(it).atZone(zone).toLocalDate()
            }
        }
        .map { (day, dayEvents) ->
            val header = when (day) {
                null -> null
                today -> UiText.Resource(R.string.today)
                today.plusDays(1) -> UiText.Resource(R.string.tomorrow)
                else -> UiText.Raw("${weekdayFormat.format(day)}, ${dateFormat.format(day)}")
            }
            EpgSearchSection(header, dayEvents)
        }
}
