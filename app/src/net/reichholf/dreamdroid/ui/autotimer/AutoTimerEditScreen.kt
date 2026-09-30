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
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
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
import net.reichholf.dreamdroid.ui.epg.EpgDatePickerDialog
import net.reichholf.dreamdroid.ui.epg.EpgTimePickerDialog
import net.reichholf.dreamdroid.ui.text.asString

/** Changes of the editor form; the match and name are edited through their text fields. */
interface AutoTimerEditActions {
    fun setSearchType(type: SearchType)
    fun setCaseSensitive(sensitive: Boolean)
    fun setEnabled(enabled: Boolean)
    fun setZap(zap: Boolean)
    fun removeTarget(target: Target)
    fun setTimeWindow(on: Boolean)
    fun toggleDay(day: DayFilter)
    fun setDateWindow(on: Boolean)
    fun openPicker(pick: AutoTimerEditPick)
    fun dismissPicker()
    fun onTimePicked(hour: Int, minute: Int)
    fun onDatePicked(utcDateMillis: Long)
    fun reload()
}

/** The editor, or why there is none; [onPickTargets] opens the channel picker. */
@Composable
fun AutoTimerEditScreen(
    state: AutoTimerEditUiState,
    match: TextFieldState,
    name: TextFieldState,
    actions: AutoTimerEditActions,
    onPickTargets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = state.content) {
            AutoTimerEditContent.Loading -> ListEmptyState(loading = true, message = null)

            AutoTimerEditContent.Editing -> AutoTimerEditForm(
                state = state,
                match = match,
                name = name,
                actions = actions,
                onPickTargets = onPickTargets
            )

            AutoTimerEditContent.Changed -> ChangedOnReceiver(onReload = actions::reload)

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
            text = stringResource(R.string.autotimer_changed),
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
    match: TextFieldState,
    name: TextFieldState,
    actions: AutoTimerEditActions,
    onPickTargets: () -> Unit
) {
    val draft = state.draft
    EditFormColumn {
        EditFormSection {
            EditOutlinedTextField(
                state = match,
                label = stringResource(R.string.autotimer_match_label),
                isError = state.matchError != null,
                supportingText = state.matchError?.asString()
            )
            EditOutlinedTextField(
                state = name,
                label = stringResource(R.string.autotimer_name_label),
                supportingText = stringResource(R.string.autotimer_name_hint)
            )
            val searchTypes = SearchType.entries
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
        TargetsSection(draft = draft, actions = actions, onPickTargets = onPickTargets)
        WhenSection(draft = draft, actions = actions)
        state.loaded?.extras?.let { ExtrasNote(it) }
    }
}

@Composable
private fun TargetsSection(
    draft: AutoTimerSettings,
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
                val label = target.name.ifBlank { target.ref }
                val remove = stringResource(R.string.autotimer_remove_target, label)
                InputChip(
                    selected = false,
                    onClick = { if (sendable) actions.removeTarget(target) },
                    enabled = sendable,
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
        }
        if (sendable) {
            OutlinedButton(onClick = onPickTargets) {
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
private fun WhenSection(draft: AutoTimerSettings, actions: AutoTimerEditActions) {
    EditFormSection(title = stringResource(R.string.autotimer_when)) {
        EditSwitchRow(
            checked = draft.timeWindow != null,
            onCheckedChange = actions::setTimeWindow,
            label = stringResource(R.string.autotimer_time_window)
        )
        draft.timeWindow?.let { window ->
            EditPairedRow {
                EditPickField(
                    value = formatClock(window.from),
                    label = stringResource(R.string.autotimer_from),
                    onClick = { actions.openPicker(AutoTimerEditPick.TimeFrom) },
                    modifier = Modifier.weight(1f)
                )
                EditPickField(
                    value = formatClock(window.to),
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
private fun ExtrasNote(extras: Extras) {
    val names = listOfNotNull(
        stringResource(R.string.autotimer_extra_counter).takeIf { extras.counter },
        stringResource(R.string.autotimer_extra_vps).takeIf { extras.vps },
        stringResource(R.string.autotimer_extra_series).takeIf { extras.seriesPlugin },
        stringResource(R.string.autotimer_extra_alternatives).takeIf { extras.overrideAlternatives }
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

        null -> Unit
    }
}

private val SearchType.label: Int
    get() = when (this) {
        SearchType.Partial -> R.string.autotimer_search_partial
        SearchType.Exact -> R.string.autotimer_search_exact
        SearchType.Description -> R.string.autotimer_search_description
    }

private fun formatClock(time: LocalTime): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun formatDay(day: Instant): String =
    day.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
