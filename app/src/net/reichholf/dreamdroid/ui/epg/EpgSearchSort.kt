package net.reichholf.dreamdroid.ui.epg

import androidx.annotation.StringRes
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event

/** The order of EPG search results. */
enum class EpgSearchSort(@param:StringRes val label: Int) {
    /** Chronological, grouped by day; the default. */
    Date(R.string.epg_search_sort_date),

    /** Alphabetical by channel, then start. */
    Channel(R.string.epg_search_sort_channel),

    /** Alphabetical by title, then start. */
    Title(R.string.epg_search_sort_title)
}

/**
 * [events] in [sort] order. Date keeps the day sections; the other orders are one flat
 * section whose rows show their date.
 */
internal fun epgSearchSections(
    events: List<Event>,
    sort: EpgSearchSort,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowSec: Long = System.currentTimeMillis() / 1000L
): List<EpgDaySection> = when (sort) {
    EpgSearchSort.Date -> epgDaySections(events, today, zone, locale, nowSec)
    EpgSearchSort.Channel -> flatSection(events, channelOrder(locale))
    EpgSearchSort.Title -> flatSection(events, titleOrder(locale))
}

private fun flatSection(events: List<Event>, order: Comparator<Event>): List<EpgDaySection> =
    if (events.isEmpty()) {
        emptyList()
    } else {
        listOf(EpgDaySection(day = null, events = events.sortedWith(order)))
    }

private fun channelOrder(locale: Locale): Comparator<Event> =
    compareBy<Event> { it.serviceName.lowercase(locale) }
        .thenBy { it.startOrMax() }
        .thenBy { it.title.lowercase(locale) }

private fun titleOrder(locale: Locale): Comparator<Event> =
    compareBy<Event> { it.title.lowercase(locale) }
        .thenBy { it.startOrMax() }
        .thenBy { it.serviceName.lowercase(locale) }

private fun Event.startOrMax(): Long = start.trim().toLongOrNull() ?: Long.MAX_VALUE
