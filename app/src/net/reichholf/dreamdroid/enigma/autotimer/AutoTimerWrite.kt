package net.reichholf.dreamdroid.enigma.autotimer

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * A write through `/autotimer/edit`. The plugin updates only the fields a request names, so a
 * write names the field groups that changed, and never a field dreamDroid does not model.
 */
sealed interface AutoTimerWrite {
    /** [edited] replaces [loaded]'s settings on the box. */
    data class Change(val loaded: AutoTimer, val edited: AutoTimerSettings) : AutoTimerWrite

    /**
     * A new AutoTimer. The plugin starts it as a copy of its defaults, so only what [edited]
     * changes against [defaults] is sent.
     */
    data class Create(val defaults: AutoTimerSettings, val edited: AutoTimerSettings) :
        AutoTimerWrite
}

/** What the plugin updates together. A changed group sends all of its keys. */
enum class FieldGroup {
    Enabled,
    Search,
    TimeWindow,
    DateWindow,
    Offset,
    MaxDuration,
    Location,
    Targets,
    Tags,
    Include,
    Exclude,
    AfterEvent,
    RecordMode,
    Duplicates
}

/** The groups [edited] changes against [base]. A write can never set several after events. */
fun changedGroups(base: AutoTimerSettings, edited: AutoTimerSettings): Set<FieldGroup> =
    FieldGroup.entries.filterTo(LinkedHashSet()) { group ->
        when (group) {
            FieldGroup.Enabled -> base.enabled != edited.enabled

            FieldGroup.Search ->
                base.searchType != edited.searchType ||
                    base.caseSensitive != edited.caseSensitive

            FieldGroup.TimeWindow -> base.timeWindow != edited.timeWindow

            FieldGroup.DateWindow -> base.dateWindow != edited.dateWindow

            FieldGroup.Offset -> base.offset != edited.offset

            FieldGroup.MaxDuration -> base.maxDurationMinutes != edited.maxDurationMinutes

            FieldGroup.Location -> base.location != edited.location

            FieldGroup.Targets -> base.targets != edited.targets && edited.targetsSendable

            FieldGroup.Tags -> base.tags != edited.tags

            FieldGroup.Include -> base.include != edited.include

            FieldGroup.Exclude -> base.exclude != edited.exclude

            FieldGroup.AfterEvent ->
                base.afterEvent != edited.afterEvent &&
                    edited.afterEvent !is AfterEvent.Several

            FieldGroup.RecordMode -> base.recordMode != edited.recordMode

            FieldGroup.Duplicates -> base.duplicates != edited.duplicates
        }
    }

/** The plugin splits `services` and `bouquets` at commas, so a ref with one cannot be sent. */
val AutoTimerSettings.targetsSendable: Boolean
    get() = targets.none { ',' in it.ref }

val AutoTimerWrite.groups: Set<FieldGroup>
    get() = when (this) {
        is AutoTimerWrite.Change -> changedGroups(loaded.settings, edited)
        is AutoTimerWrite.Create -> changedGroups(defaults, edited)
    }

/**
 * The `edit` parameters. `match` and `name` always go along: the plugin decodes the stored
 * values again when they are missing (`AutoTimerResource.py`).
 */
fun AutoTimerWrite.toParams(): List<NameValuePair> {
    val edited = when (this) {
        is AutoTimerWrite.Change -> edited
        is AutoTimerWrite.Create -> edited
    }
    return buildList {
        if (this@toParams is AutoTimerWrite.Change) {
            add(NameValuePair("id", loaded.id.value.toString()))
        }
        add(NameValuePair("match", escape(edited.match)))
        add(NameValuePair("name", escape(edited.name)))
        groups.forEach { addAll(it.params(edited)) }
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
