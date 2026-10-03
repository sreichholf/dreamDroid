package net.reichholf.dreamdroid.ui.autotimer

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.autotimer.AfterEvent
import net.reichholf.dreamdroid.enigma.autotimer.AfterEventAction
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.DescriptionCompare
import net.reichholf.dreamdroid.enigma.autotimer.DuplicateCheck
import net.reichholf.dreamdroid.enigma.autotimer.DuplicateScope
import net.reichholf.dreamdroid.enigma.autotimer.Extras
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.enigma.autotimer.targetsSendable
import net.reichholf.dreamdroid.ui.compose.EditDropdownField
import net.reichholf.dreamdroid.ui.compose.EditFormColumn
import net.reichholf.dreamdroid.ui.compose.EditFormSection
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.compose.EditPairedRow
import net.reichholf.dreamdroid.ui.compose.EditPickField
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.epg.EpgDatePickerDialog
import net.reichholf.dreamdroid.ui.epg.EpgTimePickerDialog
import net.reichholf.dreamdroid.ui.text.asString

/** The editor's text fields, which the ViewModel owns. */
class AutoTimerEditFields(
    val match: TextFieldState,
    val name: TextFieldState,
    val filter: TextFieldState,
    val offsetBefore: TextFieldState,
    val offsetAfter: TextFieldState,
    val maxDuration: TextFieldState
)

/** Changes of the editor form; text is edited through [AutoTimerEditFields]. */
interface AutoTimerEditActions {
    fun setSearchType(type: SearchType)
    fun setCaseSensitive(sensitive: Boolean)
    fun setEnabled(enabled: Boolean)
    fun setZap(zap: Boolean)
    fun removeTarget(target: Target)
    fun setTimeWindow(on: Boolean)
    fun toggleDay(day: DayFilter)
    fun setDateWindow(on: Boolean)
    fun applySuggestedWindow()
    fun openPicker(pick: AutoTimerEditPick)
    fun dismissPicker()
    fun onTimePicked(hour: Int, minute: Int)
    fun onDatePicked(utcDateMillis: Long)
    fun setFilterKind(kind: FilterKind)
    fun addFilter()
    fun removeFilter(kind: FilterKind, value: String)
    fun setOffset(on: Boolean)
    fun setMaxDuration(on: Boolean)
    fun setLocation(location: String?)
    fun onTagsPicked(tags: List<String>)
    fun setAfterEvent(action: AfterEventAction?)
    fun setSetEndTime(setEndTime: Boolean)
    fun setDuplicateScope(scope: DuplicateScope?)
    fun setDuplicateCompare(compare: DescriptionCompare)
    fun reload()
}

/** The editor, or why there is none; [onPickTargets] opens the channel picker. */
@Composable
fun AutoTimerEditScreen(
    state: AutoTimerEditUiState,
    fields: AutoTimerEditFields,
    actions: AutoTimerEditActions,
    onPickTargets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = state.content) {
            AutoTimerEditContent.Loading -> ListEmptyState(loading = true, message = null)

            AutoTimerEditContent.Editing -> AutoTimerEditForm(
                state = state,
                fields = fields,
                actions = actions,
                onPickTargets = onPickTargets
            )

            AutoTimerEditContent.Changed -> ChangedOnReceiver(onReload = actions::reload)

            is AutoTimerEditContent.Saved -> ListEmptyState(
                loading = false,
                message = content.message.asString() + "\n\n" +
                    stringResource(R.string.autotimer_edit_saved_unlisted)
            )

            AutoTimerEditContent.OtherReceiver -> ListEmptyState(
                loading = false,
                message = stringResource(R.string.autotimer_edit_other_receiver)
            )

            AutoTimerEditContent.Gone -> ListEmptyState(
                loading = false,
                message = stringResource(R.string.autotimer_gone)
            )

            AutoTimerEditContent.PluginMissing -> ListEmptyState(
                loading = false,
                message = stringResource(R.string.autotimer_not_installed),
                onRetry = actions::reload
            )

            is AutoTimerEditContent.Failed -> ListEmptyState(
                loading = false,
                message = content.message.asString(),
                onRetry = actions::reload
            )
        }
        if (state.saving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    AutoTimerEditPickers(state = state, actions = actions)
}

@Composable
private fun ChangedOnReceiver(onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.autotimer_edit_changed),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onReload) {
            Text(stringResource(R.string.reload))
        }
    }
}

@Composable
private fun AutoTimerEditForm(
    state: AutoTimerEditUiState,
    fields: AutoTimerEditFields,
    actions: AutoTimerEditActions,
    onPickTargets: () -> Unit
) {
    val draft = state.draft
    EditFormColumn {
        EditFormSection {
            EditOutlinedTextField(
                state = fields.match,
                label = stringResource(R.string.autotimer_match_label),
                isError = state.matchError != null,
                supportingText = state.matchError?.asString()
            )
            EditOutlinedTextField(
                state = fields.name,
                label = stringResource(R.string.autotimer_name_label),
                supportingText = stringResource(R.string.autotimer_name_hint)
            )
            // Only oe-alliance's plugin knows "start"; it is offered where it is set already.
            val searchTypes = SearchType.entries.filter {
                it != SearchType.Start || SearchType.Start in
                    listOf(draft.searchType, state.base.searchType)
            }
            EditDropdownField(
                options = searchTypes.map { stringResource(it.label) },
                selectedIndex = searchTypes.indexOf(draft.searchType),
                onSelected = { actions.setSearchType(searchTypes[it]) },
                label = stringResource(R.string.autotimer_search_type)
            )
            EditSwitchRow(
                checked = draft.caseSensitive,
                onCheckedChange = actions::setCaseSensitive,
                label = stringResource(R.string.autotimer_case_sensitive)
            )
            EditSwitchRow(
                checked = draft.enabled,
                onCheckedChange = actions::setEnabled,
                label = stringResource(R.string.enabled)
            )
            EditSwitchRow(
                checked = draft.recordMode is RecordMode.Zap,
                onCheckedChange = actions::setZap,
                label = stringResource(R.string.autotimer_zap)
            )
        }
        TargetsSection(
            draft = draft,
            editable = state.editable,
            actions = actions,
            onPickTargets = onPickTargets
        )
        WhenSection(state = state, actions = actions)
        FiltersSection(state = state, filter = fields.filter, actions = actions)
        RecordingSection(state = state, fields = fields, actions = actions)
        state.loaded?.extras?.let { ExtrasNote(it) }
    }
}

@Composable
private fun TargetsSection(
    draft: AutoTimerSettings,
    editable: Boolean,
    actions: AutoTimerEditActions,
    onPickTargets: () -> Unit
) {
    val sendable = draft.targetsSendable
    EditFormSection(title = stringResource(R.string.autotimer_targets)) {
        if (draft.targets.isEmpty()) {
            Text(
                text = stringResource(R.string.autotimer_all_channels),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.targets.forEach { target ->
                RemovableChip(
                    label = target.name.ifBlank { target.ref },
                    onRemove = { actions.removeTarget(target) },
                    enabled = sendable
                )
            }
        }
        if (sendable) {
            OutlinedButton(onClick = onPickTargets, enabled = editable) {
                Text(stringResource(R.string.autotimer_add_channels))
            }
        } else {
            Text(
                text = stringResource(R.string.autotimer_targets_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhenSection(state: AutoTimerEditUiState, actions: AutoTimerEditActions) {
    val draft = state.draft
    val suggested = state.suggestedWindow
    val clock = rememberClockFormat()
    EditFormSection(title = stringResource(R.string.autotimer_when)) {
        EditSwitchRow(
            checked = draft.timeWindow != null,
            onCheckedChange = actions::setTimeWindow,
            label = stringResource(R.string.autotimer_time_window)
        )
        if (draft.timeWindow == null && suggested != null) {
            SuggestionChip(
                onClick = actions::applySuggestedWindow,
                label = {
                    Text(
                        stringResource(
                            R.string.autotimer_suggested_window,
                            suggested.from.format(clock),
                            suggested.to.format(clock)
                        )
                    )
                }
            )
        }
        draft.timeWindow?.let { window ->
            EditPairedRow {
                EditPickField(
                    value = window.from.format(clock),
                    label = stringResource(R.string.autotimer_from),
                    onClick = { actions.openPicker(AutoTimerEditPick.TimeFrom) },
                    modifier = Modifier.weight(1f)
                )
                EditPickField(
                    value = window.to.format(clock),
                    label = stringResource(R.string.autotimer_to),
                    onClick = { actions.openPicker(AutoTimerEditPick.TimeTo) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = stringResource(R.string.autotimer_days),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DayChips(selected = draft.include.days, onToggle = actions::toggleDay)
        EditSwitchRow(
            checked = draft.dateWindow != null,
            onCheckedChange = actions::setDateWindow,
            label = stringResource(R.string.autotimer_date_window)
        )
        draft.dateWindow?.let { window ->
            EditPairedRow {
                EditPickField(
                    value = formatDay(window.after),
                    label = stringResource(R.string.autotimer_date_after),
                    onClick = { actions.openPicker(AutoTimerEditPick.DateAfter) },
                    modifier = Modifier.weight(1f)
                )
                EditPickField(
                    value = formatDay(window.before),
                    label = stringResource(R.string.autotimer_date_before),
                    onClick = { actions.openPicker(AutoTimerEditPick.DateBefore) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.dateWindowInvalid) {
                Text(
                    text = stringResource(R.string.autotimer_date_reversed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DayChips(selected: List<DayFilter>, onToggle: (DayFilter) -> Unit) {
    val options = DayOfWeek.entries.map { DayFilter.On(it) } +
        listOf(DayFilter.Weekdays, DayFilter.Weekend)
    val weekdays = stringResource(R.string.autotimer_weekdays)
    val weekend = stringResource(R.string.autotimer_weekend)
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = {
                    Text(
                        when (day) {
                            is DayFilter.On ->
                                day.day.getDisplayName(TextStyle.SHORT, locale)

                            DayFilter.Weekdays -> weekdays

                            DayFilter.Weekend -> weekend
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun FiltersSection(
    state: AutoTimerEditUiState,
    filter: TextFieldState,
    actions: AutoTimerEditActions
) {
    EditFormSection(title = stringResource(R.string.autotimer_filters)) {
        FilterKind.entries.forEach { kind ->
            val values = kind.values(state.draft)
            if (values.isNotEmpty()) {
                Text(
                    text = stringResource(kind.label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { value ->
                        RemovableChip(label = value, onRemove = {
                            actions.removeFilter(kind, value)
                        })
                    }
                }
            }
        }
        val kinds = FilterKind.entries
        EditDropdownField(
            options = kinds.map { stringResource(it.label) },
            selectedIndex = kinds.indexOf(state.filterKind),
            onSelected = { actions.setFilterKind(kinds[it]) },
            label = stringResource(R.string.autotimer_filter_kind)
        )
        EditOutlinedTextField(
            state = filter,
            label = stringResource(R.string.autotimer_filter_text)
        )
        OutlinedButton(onClick = actions::addFilter) {
            Text(stringResource(R.string.autotimer_filter_add))
        }
    }
}

@Composable
private fun RecordingSection(
    state: AutoTimerEditUiState,
    fields: AutoTimerEditFields,
    actions: AutoTimerEditActions
) {
    val draft = state.draft
    val minutes = stringResource(R.string.autotimer_minutes_suffix)
    EditFormSection(title = stringResource(R.string.autotimer_recording)) {
        EditSwitchRow(
            checked = draft.offset != null,
            onCheckedChange = actions::setOffset,
            label = stringResource(R.string.autotimer_offset)
        )
        if (draft.offset != null) {
            EditPairedRow {
                EditOutlinedTextField(
                    state = fields.offsetBefore,
                    label = stringResource(R.string.autotimer_offset_before),
                    keyboardType = KeyboardType.Number,
                    suffix = minutes,
                    isError = state.offsetError != null,
                    modifier = Modifier.weight(1f)
                )
                EditOutlinedTextField(
                    state = fields.offsetAfter,
                    label = stringResource(R.string.autotimer_offset_after),
                    keyboardType = KeyboardType.Number,
                    suffix = minutes,
                    isError = state.offsetError != null,
                    supportingText = state.offsetError?.asString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        EditSwitchRow(
            checked = draft.maxDurationMinutes != null,
            onCheckedChange = actions::setMaxDuration,
            label = stringResource(R.string.autotimer_max_duration)
        )
        if (draft.maxDurationMinutes != null) {
            EditOutlinedTextField(
                state = fields.maxDuration,
                label = stringResource(R.string.autotimer_max_duration),
                keyboardType = KeyboardType.Number,
                suffix = minutes,
                isError = state.maxDurationError != null,
                supportingText = state.maxDurationError?.asString()
            )
        }
        val locations = listOf(null) + (state.locations + listOfNotNull(draft.location)).distinct()
        EditDropdownField(
            options = locations.map { it ?: stringResource(R.string.autotimer_receiver_default) },
            selectedIndex = locations.indexOf(draft.location),
            onSelected = { actions.setLocation(locations[it]) },
            label = stringResource(R.string.location)
        )
        EditPickField(
            value = draft.tags.joinToString(" ").ifEmpty { stringResource(R.string.none) },
            label = stringResource(R.string.tags),
            onClick = { actions.openPicker(AutoTimerEditPick.Tags) }
        )
        AfterEventField(draft = draft, actions = actions)
        val zap = draft.recordMode as? RecordMode.Zap
        if (zap != null) {
            EditSwitchRow(
                checked = zap.setEndTime,
                onCheckedChange = actions::setSetEndTime,
                label = stringResource(R.string.autotimer_set_end_time)
            )
        }
        DuplicatesFields(draft = draft, actions = actions)
    }
}

@Composable
private fun AfterEventField(draft: AutoTimerSettings, actions: AutoTimerEditActions) {
    val afterEvent = draft.afterEvent
    if (afterEvent is AfterEvent.Several) {
        Text(
            text = stringResource(R.string.autotimer_after_event_several),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val options = listOf(null) + AfterEventAction.entries
    EditDropdownField(
        options = options.map { stringResource(it.label) },
        selectedIndex = options.indexOf((afterEvent as? AfterEvent.Fixed)?.action),
        onSelected = { actions.setAfterEvent(options[it]) },
        label = stringResource(R.string.autotimer_after_event)
    )
}

@Composable
private fun DuplicatesFields(draft: AutoTimerSettings, actions: AutoTimerEditActions) {
    val check = draft.duplicates as? DuplicateCheck.On
    val scopes = listOf(null) + DuplicateScope.entries
    EditDropdownField(
        options = scopes.map { stringResource(it.label) },
        selectedIndex = scopes.indexOf(check?.scope),
        onSelected = { actions.setDuplicateScope(scopes[it]) },
        label = stringResource(R.string.autotimer_duplicates)
    )
    if (check != null) {
        val compares = DescriptionCompare.entries
        EditDropdownField(
            options = compares.map { stringResource(it.label) },
            selectedIndex = compares.indexOf(check.compare),
            onSelected = { actions.setDuplicateCompare(compares[it]) },
            label = stringResource(R.string.autotimer_duplicates_compare)
        )
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit, enabled: Boolean = true) {
    val remove = stringResource(R.string.autotimer_remove_target, label)
    InputChip(
        selected = false,
        onClick = { if (enabled) onRemove() },
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_action_close),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize)
            )
        },
        modifier = Modifier.semantics { contentDescription = remove }
    )
}

@Composable
private fun ExtrasNote(extras: Extras) {
    val names = listOfNotNull(
        stringResource(R.string.autotimer_extra_counter).takeIf { extras.counter },
        stringResource(R.string.autotimer_extra_vps).takeIf { extras.vps },
        stringResource(R.string.autotimer_extra_series).takeIf { extras.seriesPlugin },
        stringResource(R.string.autotimer_extra_alternatives).takeIf {
            extras.overrideAlternatives
        },
        stringResource(R.string.autotimer_extra_always_zap).takeIf { extras.alwaysZap }
    )
    if (names.isNotEmpty()) {
        Text(
            text = stringResource(R.string.autotimer_extras, names.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutoTimerEditPickers(state: AutoTimerEditUiState, actions: AutoTimerEditActions) {
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    when (val pick = state.picker) {
        AutoTimerEditPick.TimeFrom, AutoTimerEditPick.TimeTo -> {
            val window = state.draft.timeWindow ?: return
            val time = if (pick == AutoTimerEditPick.TimeFrom) window.from else window.to
            EpgTimePickerDialog(
                initialTimeSec = LocalDate.now(zone).atTime(time).atZone(zone).toEpochSecond()
                    .toInt(),
                is24Hour = is24Hour,
                onDismiss = actions::dismissPicker,
                onConfirm = actions::onTimePicked
            )
        }

        AutoTimerEditPick.DateAfter, AutoTimerEditPick.DateBefore -> {
            val window = state.draft.dateWindow ?: return
            val day = if (pick == AutoTimerEditPick.DateAfter) window.after else window.before
            EpgDatePickerDialog(
                initialTimeSec = day.epochSecond.toInt(),
                onDismiss = actions::dismissPicker,
                onConfirm = actions::onDatePicked
            )
        }

        AutoTimerEditPick.Tags -> {
            val choices = (state.tagChoices + state.draft.tags).distinct()
            MultiChoiceAlertDialog(
                title = stringResource(R.string.choose_tags),
                items = choices,
                initialChecked = BooleanArray(choices.size) { choices[it] in state.draft.tags },
                onDismiss = actions::dismissPicker,
                onConfirm = { indices ->
                    actions.onTagsPicked(indices.sorted().map { choices[it] })
                }
            )
        }

        null -> Unit
    }
}

private val FilterKind.label: Int
    get() = when (this) {
        FilterKind.IncludeTitle -> R.string.autotimer_filter_include_title
        FilterKind.IncludeShortDescription -> R.string.autotimer_filter_include_short
        FilterKind.IncludeDescription -> R.string.autotimer_filter_include_description
        FilterKind.ExcludeTitle -> R.string.autotimer_filter_exclude_title
        FilterKind.ExcludeShortDescription -> R.string.autotimer_filter_exclude_short
        FilterKind.ExcludeDescription -> R.string.autotimer_filter_exclude_description
    }

private val AfterEventAction?.label: Int
    get() = when (this) {
        null -> R.string.autotimer_receiver_default
        AfterEventAction.Nothing -> R.string.autotimer_after_event_nothing
        AfterEventAction.Standby -> R.string.autotimer_after_event_standby
        AfterEventAction.DeepStandby -> R.string.autotimer_after_event_deep_standby
        AfterEventAction.Auto -> R.string.autotimer_after_event_auto
    }

private val DuplicateScope?.label: Int
    get() = when (this) {
        null -> R.string.autotimer_duplicates_off
        DuplicateScope.SameService -> R.string.autotimer_duplicates_same_service
        DuplicateScope.AnyService -> R.string.autotimer_duplicates_any_service
        DuplicateScope.AnyServiceOrRecording -> R.string.autotimer_duplicates_any_recording
    }

private val DescriptionCompare.label: Int
    get() = when (this) {
        DescriptionCompare.Title -> R.string.autotimer_compare_title
        DescriptionCompare.TitleAndShort -> R.string.autotimer_compare_short
        DescriptionCompare.All -> R.string.autotimer_compare_all
    }

private val SearchType.label: Int
    get() = when (this) {
        SearchType.Partial -> R.string.autotimer_search_partial
        SearchType.Exact -> R.string.autotimer_search_exact
        SearchType.Description -> R.string.autotimer_search_description
        SearchType.Start -> R.string.autotimer_search_start
    }

private fun formatDay(day: Instant): String =
    day.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
