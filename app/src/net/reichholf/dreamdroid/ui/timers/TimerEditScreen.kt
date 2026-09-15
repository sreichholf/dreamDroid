package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditDropdownField
import net.reichholf.dreamdroid.ui.compose.EditFormColumn
import net.reichholf.dreamdroid.ui.compose.EditFormSection
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.compose.EditPairedRow
import net.reichholf.dreamdroid.ui.compose.EditPickField
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow

@Composable
fun TimerEditScreen(
    state: TimerEditState,
    saveLabel: String,
    onSave: () -> Unit,
    onPickBeginDate: () -> Unit,
    onPickBeginTime: () -> Unit,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit,
    onPickRepeated: () -> Unit,
    onPickService: () -> Unit,
    onPickTags: () -> Unit,
    showSaveFab: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Hosted under the XML app bar; default Scaffold safeDrawing would double-pad
    // and lift a FAB (#263). Bottom inset is PhoneNavHost when the shell
    // destination bar is hidden. Phone save is toolbar-only ([R.menu.save]).
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showSaveFab) {
                FloatingActionButton(onClick = onSave) {
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
            if (state.saveError.isNotEmpty()) {
                Text(
                    text = state.saveError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            EditFormSection(title = stringResource(R.string.timer)) {
                EditOutlinedTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    label = stringResource(R.string.title),
                    contentDescription = "Title"
                )
                EditOutlinedTextField(
                    value = state.description,
                    onValueChange = { state.description = it },
                    label = stringResource(R.string.description),
                    singleLine = false,
                    contentDescription = "Description"
                )
                EditSwitchRow(
                    checked = state.enabled,
                    onCheckedChange = { state.enabled = it },
                    label = stringResource(R.string.enabled)
                )
                EditSwitchRow(
                    checked = state.zap,
                    onCheckedChange = { state.zap = it },
                    label = stringResource(R.string.zap)
                )
            }

            EditFormSection {
                EditPairedRow {
                    EditPickField(
                        value = state.beginDate,
                        label = stringResource(R.string.begin_date),
                        onClick = onPickBeginDate,
                        modifier = Modifier.weight(1f)
                    )
                    EditPickField(
                        value = state.beginTime,
                        label = stringResource(R.string.begin_time),
                        onClick = onPickBeginTime,
                        modifier = Modifier.weight(1f)
                    )
                }
                EditPairedRow {
                    EditPickField(
                        value = state.endDate,
                        label = stringResource(R.string.end_date),
                        onClick = onPickEndDate,
                        modifier = Modifier.weight(1f)
                    )
                    EditPickField(
                        value = state.endTime,
                        label = stringResource(R.string.end_time),
                        onClick = onPickEndTime,
                        modifier = Modifier.weight(1f)
                    )
                }
                EditPickField(
                    value = state.repeatedLabel.ifEmpty { stringResource(R.string.none) },
                    label = stringResource(R.string.repeatings),
                    onClick = onPickRepeated
                )
            }

            EditFormSection {
                EditPickField(
                    value = state.serviceName.ifEmpty { "…" },
                    label = stringResource(R.string.service),
                    onClick = onPickService
                )
                EditDropdownField(
                    options = state.afterEventOptions,
                    selectedIndex = state.afterEventIndex,
                    onSelected = { state.afterEventIndex = it },
                    label = stringResource(R.string.afterevent)
                )
                EditDropdownField(
                    options = state.locationOptions,
                    selectedIndex = state.locationIndex,
                    onSelected = { state.locationIndex = it },
                    label = stringResource(R.string.location)
                )
                EditPickField(
                    value = state.tagsLabel.ifEmpty { "…" },
                    label = stringResource(R.string.tags),
                    onClick = onPickTags
                )
            }

            if (showSaveFab) {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}
