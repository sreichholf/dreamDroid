package net.reichholf.dreamdroid.enigma

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import net.reichholf.dreamdroid.enigma.autotimer.AfterEvent
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.DuplicateCheck
import net.reichholf.dreamdroid.enigma.autotimer.FieldGroup
import net.reichholf.dreamdroid.enigma.autotimer.Filters
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.enigma.autotimer.groups
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * The `/autotimer/edit` parameters of [write]. `match` and `name` always go along: the
 * plugin decodes the stored values again when they are missing (`AutoTimerResource.py`).
 */
internal fun autoTimerEditParams(write: AutoTimerWrite): List<NameValuePair> {
    val edited = when (write) {
        is AutoTimerWrite.Change -> write.edited
        is AutoTimerWrite.Create -> write.edited
    }
    return buildList {
        if (write is AutoTimerWrite.Change) {
            add(NameValuePair("id", write.loaded.id.value.toString()))
        }
        add(NameValuePair("match", escape(edited.match)))
        add(NameValuePair("name", escape(edited.name)))
        write.groups.forEach { addAll(it.params(edited)) }
    }
}

private fun FieldGroup.params(s: AutoTimerSettings): List<NameValuePair> = when (this) {
    FieldGroup.Enabled -> listOf(pair("enabled", if (s.enabled) "1" else "0"))

    FieldGroup.Search -> listOf(
        pair("searchType", s.searchType.token),
        pair("searchCase", if (s.caseSensitive) "sensitive" else "insensitive")
    )

    FieldGroup.TimeWindow -> listOf(
        pair("timespanFrom", s.timeWindow?.from?.let(::clock).orEmpty()),
        pair("timespanTo", s.timeWindow?.to?.let(::clock).orEmpty())
    )

    FieldGroup.DateWindow -> listOf(
        pair("after", s.dateWindow?.after?.epochSecond?.toString().orEmpty()),
        pair("before", s.dateWindow?.before?.epochSecond?.toString().orEmpty())
    )

    FieldGroup.Offset -> listOf(
        pair("offset", s.offset?.let { "${it.beforeMinutes},${it.afterMinutes}" }.orEmpty())
    )

    FieldGroup.MaxDuration -> listOf(
        pair("maxduration", s.maxDurationMinutes?.toString().orEmpty())
    )

    FieldGroup.Location -> listOf(pair("location", s.location.orEmpty()))

    FieldGroup.Targets -> listOf(
        pair("services", escape(s.targets.filterIsInstance<Target.Channel>().joinRefs())),
        pair("bouquets", escape(s.targets.filterIsInstance<Target.Bouquet>().joinRefs()))
    )

    FieldGroup.Tags -> repeated("tag", s.tags.map(::escape))

    FieldGroup.Include -> filterParams("", s.include)

    FieldGroup.Exclude -> filterParams("!", s.exclude)

    FieldGroup.AfterEvent -> when (val event = s.afterEvent) {
        AfterEvent.ReceiverDefault -> listOf(pair("afterevent", "default"))

        is AfterEvent.Fixed -> listOfNotNull(
            pair("afterevent", event.action.editToken),
            event.window?.let { pair("aftereventFrom", clock(it.from)) },
            event.window?.let { pair("aftereventTo", clock(it.to)) }
        )

        is AfterEvent.Several -> emptyList()
    }

    FieldGroup.RecordMode -> when (val mode = s.recordMode) {
        RecordMode.Record -> listOf(pair("justplay", "0"))

        is RecordMode.Zap -> listOf(
            pair("justplay", "1"),
            pair("setEndtime", if (mode.setEndTime) "1" else "0")
        )
    }

    FieldGroup.Duplicates -> when (val check = s.duplicates) {
        DuplicateCheck.Off -> listOf(pair("avoidDuplicateDescription", "0"))

        is DuplicateCheck.On -> listOf(
            pair("avoidDuplicateDescription", check.scope.code.toString()),
            pair("searchForDuplicateDescription", check.compare.code.toString())
        )
    }
}

/** All four keys of a filter group, so the plugin keeps none of the old lists. */
private fun filterParams(prefix: String, filters: Filters): List<NameValuePair> =
    repeated("${prefix}title", filters.title.map(::escape)) +
        repeated("${prefix}shortdescription", filters.shortDescription.map(::escape)) +
        repeated("${prefix}description", filters.description.map(::escape)) +
        repeated("${prefix}dayofweek", filters.days.map { it.token })

/** One pair per value; one empty value clears the list on the box. */
private fun repeated(key: String, values: List<String>): List<NameValuePair> =
    values.ifEmpty { listOf("") }.map { pair(key, it) }

private fun List<Target>.joinRefs(): String = joinToString(",") { it.ref }

/** The plugin decodes these values once more after the web server did. */
private fun escape(value: String): String = value.replace("%", "%25")

private fun pair(key: String, value: String) = NameValuePair(key, value)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(time: LocalTime): String = time.format(CLOCK)
