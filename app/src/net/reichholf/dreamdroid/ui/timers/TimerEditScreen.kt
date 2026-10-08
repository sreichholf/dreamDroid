package net.reichholf.dreamdroid.ui.timers

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.ui.compose.EditDropdownField
import net.reichholf.dreamdroid.ui.compose.EditFormColumn
import net.reichholf.dreamdroid.ui.compose.EditFormSection
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.compose.EditPairedRow
import net.reichholf.dreamdroid.ui.compose.EditPickField
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.epg.EpgDatePickerDialog
import net.reichholf.dreamdroid.ui.epg.EpgTimePickerDialog
import net.reichholf.dreamdroid.ui.text.asString

/** A field of the timer form that opens a picker. */
enum class TimerEditPick {
    BeginDate,
    BeginTime,
    EndDate,
    EndTime,
    VpsDate,
    VpsTime,
    Repeated,
    Service,
    Tags
}

/**
 * The timer form of [uiState] plus its pickers and request progress. The service pick is
 * the host's: [onPickService] runs instead of a dialog.
 */
@Composable
fun TimerEditContent(
    uiState: TimerEditUiState,
    name: TextFieldState,
    description: TextFieldState,
    actions: TimerFormActions,
    onPickService: () -> Unit,
    modifier: Modifier = Modifier,
    showSaveFab: Boolean = false,
    onSave: () -> Unit = {},
    initialFocusRequester: FocusRequester? = null
) {
    val form = uiState.form(name.text) ?: return
    var picker by remember { mutableStateOf<TimerEditPick?>(null) }
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    TimerEditScreen(
        form = form,
        name = name,
        description = description,
        actions = actions,
        onPick = { pick ->
            if (pick == TimerEditPick.Service) onPickService() else picker = pick
        },
        saveError = uiState.saveError?.asString(),
        showSaveFab = showSaveFab,
        mutating = uiState.progress != null,
        onSave = onSave,
        initialFocusRequester = initialFocusRequester,
        modifier = modifier
    )

    val dismiss = { picker = null }
    when (picker) {
        TimerEditPick.Repeated -> MultiChoiceAlertDialog(
            title = stringResource(R.string.choose_days),
            items = stringArrayResource(R.array.weekdays).toList(),
            initialChecked = form.repeatedDays,
            onDismiss = dismiss,
            onConfirm = { days ->
                actions.onRepeatedChange(days)
                dismiss()
            }
        )

        TimerEditPick.Tags -> MultiChoiceAlertDialog(
            title = stringResource(R.string.choose_tags),
            items = uiState.tags,
            initialChecked = BooleanArray(uiState.tags.size) { uiState.tags[it] in form.tags },
            onDismiss = dismiss,
            onConfirm = { indices ->
                actions.onTagsChange(indices)
                dismiss()
            }
        )

        TimerEditPick.BeginDate, TimerEditPick.EndDate -> {
            val isBegin = picker == TimerEditPick.BeginDate
            EpgDatePickerDialog(
                initialTimeSec = if (isBegin) form.begin else form.end,
                onDismiss = dismiss,
                onConfirm = { utcDateMillis ->
                    actions.onDatePicked(isBegin, utcDateMillis)
                    dismiss()
                }
            )
        }

        TimerEditPick.BeginTime, TimerEditPick.EndTime -> {
            val isBegin = picker == TimerEditPick.BeginTime
            EpgTimePickerDialog(
                initialTimeSec = if (isBegin) form.begin else form.end,
                is24Hour = is24Hour,
                onDismiss = dismiss,
                onConfirm = { hour, minute ->
                    actions.onTimePicked(isBegin, hour, minute)
                    dismiss()
                }
            )
        }

        TimerEditPick.VpsDate -> EpgDatePickerDialog(
            initialTimeSec = form.vpsTimeOrBegin(),
            onDismiss = dismiss,
            onConfirm = { utcDateMillis ->
                actions.onVpsDatePicked(utcDateMillis)
                dismiss()
            }
        )

        TimerEditPick.VpsTime -> EpgTimePickerDialog(
            initialTimeSec = form.vpsTimeOrBegin(),
            is24Hour = is24Hour,
            onDismiss = dismiss,
            onConfirm = { hour, minute ->
                actions.onVpsTimePicked(hour, minute)
                dismiss()
            }
        )

        TimerEditPick.Service, null -> Unit
    }

    IndeterminateProgressHost(
        uiState.progress?.let { IndeterminateProgressState(message = it.asString()) }
    )
}

@Composable
fun TimerEditScreen(
    form: TimerEditForm,
    name: TextFieldState,
    description: TextFieldState,
    actions: TimerFormActions,
    onPick: (TimerEditPick) -> Unit,
    modifier: Modifier = Modifier,
    saveError: String? = null,
    showSaveFab: Boolean = true,
    mutating: Boolean = false,
    onSave: () -> Unit = {},
    initialFocusRequester: FocusRequester? = null
) {
    val resources = LocalResources.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    fun date(seconds: Int) = dateFormat.format(Date(seconds.toLong() * 1000))
    fun time(seconds: Int) = timeFormat.format(Date(seconds.toLong() * 1000))
    val saveLabel = stringResource(R.string.save)

    // Hosted under the shell app bar; default Scaffold safeDrawing would double-pad
    // and lift a FAB (#263). Bottom inset is PhoneNavHost when the shell
    // destination bar is hidden. Phone save/delete is top-bar only.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showSaveFab) {
                FloatingActionButton(
                    onClick = { if (!mutating) onSave() }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_save),
                        contentDescription = saveLabel
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        EditFormColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!saveError.isNullOrEmpty()) {
                Text(
                    text = saveError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            EditFormSection(title = stringResource(R.string.timer)) {
                EditOutlinedTextField(
                    state = name,
                    label = stringResource(R.string.title),
                    contentDescription = "Title"
                )
                EditOutlinedTextField(
                    state = description,
                    label = stringResource(R.string.description),
                    singleLine = false,
                    contentDescription = "Description"
                )
                // Initial focus goes to the first control that is not a text field, so
                // opening the editor on a TV does not pop up the soft keyboard.
                EditSwitchRow(
                    checked = form.enabled,
                    onCheckedChange = actions::onEnabledChange,
                    label = stringResource(R.string.enabled),
                    modifier =
                        initialFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                )
                EditSwitchRow(
                    checked = form.zap,
                    onCheckedChange = actions::onZapChange,
                    label = stringResource(R.string.zap)
                )
            }

            EditFormSection {
                EditPairedRow {
                    EditPickField(
                        value = date(form.begin),
                        label = stringResource(R.string.begin_date),
                        onClick = { onPick(TimerEditPick.BeginDate) },
                        modifier = Modifier.weight(1f)
                    )
                    EditPickField(
                        value = time(form.begin),
                        label = stringResource(R.string.begin_time),
                        onClick = { onPick(TimerEditPick.BeginTime) },
                        modifier = Modifier.weight(1f)
                    )
                }
                EditPairedRow {
                    EditPickField(
                        value = date(form.end),
                        label = stringResource(R.string.end_date),
                        onClick = { onPick(TimerEditPick.EndDate) },
                        modifier = Modifier.weight(1f)
                    )
                    EditPickField(
                        value = time(form.end),
                        label = stringResource(R.string.end_time),
                        onClick = { onPick(TimerEditPick.EndTime) },
                        modifier = Modifier.weight(1f)
                    )
                }
                EditPickField(
                    value = timerRepeatedLabel(resources, form.repeated),
                    label = stringResource(R.string.repeatings),
                    onClick = { onPick(TimerEditPick.Repeated) }
                )
            }

            EditFormSection {
                EditPickField(
                    value = form.serviceName.ifEmpty { "…" },
                    label = stringResource(R.string.service),
                    onClick = { onPick(TimerEditPick.Service) }
                )
                EditDropdownField(
                    options = stringArrayResource(R.array.afterevents).toList(),
                    selectedIndex = form.afterEvent,
                    onSelected = actions::onAfterEventChange,
                    label = stringResource(R.string.afterevent)
                )
                form.vps?.let { vps ->
                    EditDropdownField(
                        options = stringArrayResource(R.array.vps_modes).toList(),
                        selectedIndex = vps.mode.ordinal,
                        onSelected = { actions.onVpsModeChange(VpsMode.entries[it]) },
                        label = stringResource(R.string.vps),
                        supportingText = if (vps.mode != VpsMode.Off) {
                            stringResource(R.string.vps_note)
                        } else {
                            null
                        }
                    )
                    if (vps.showsTime) {
                        val vpsTime = form.vpsTimeOrBegin()
                        EditPairedRow {
                            EditPickField(
                                value = date(vpsTime),
                                label = stringResource(R.string.vps_date),
                                onClick = { onPick(TimerEditPick.VpsDate) },
                                modifier = Modifier.weight(1f)
                            )
                            EditPickField(
                                value = time(vpsTime),
                                label = stringResource(R.string.vps_time),
                                onClick = { onPick(TimerEditPick.VpsTime) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                EditDropdownField(
                    options = form.locations,
                    selectedIndex = form.locationIndex,
                    onSelected = actions::onLocationChange,
                    label = stringResource(R.string.location)
                )
                EditPickField(
                    value = form.tags.joinToString(" ").ifEmpty { "…" },
                    label = stringResource(R.string.tags),
                    onClick = { onPick(TimerEditPick.Tags) }
                )
            }

            if (showSaveFab) {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

/** The VPS time the pickers show: the set one, else the begin it follows. */
private fun TimerEditForm.vpsTimeOrBegin(): Int = vps?.time ?: begin
