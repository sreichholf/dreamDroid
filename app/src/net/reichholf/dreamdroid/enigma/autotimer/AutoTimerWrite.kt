package net.reichholf.dreamdroid.enigma.autotimer

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
