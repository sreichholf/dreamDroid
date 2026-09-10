package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
) {
    // Hosted under simple_layout_with_toolbar which already fits system windows.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onSave) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_save),
                    contentDescription = saveLabel,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Title" },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CheckRow(
                    checked = state.enabled,
                    onCheckedChange = { state.enabled = it },
                    label = stringResource(R.string.enabled),
                )
                CheckRow(
                    checked = state.zap,
                    onCheckedChange = { state.zap = it },
                    label = stringResource(R.string.zap),
                )
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = { state.description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Description" },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            SectionHeader(stringResource(R.string.begin_time))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectableValue(
                    value = state.beginDate,
                    contentDescription = stringResource(R.string.begin_date),
                    onClick = onPickBeginDate,
                    modifier = Modifier.weight(1f),
                )
                SelectableValue(
                    value = state.beginTime,
                    contentDescription = stringResource(R.string.begin_time),
                    onClick = onPickBeginTime,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader(stringResource(R.string.end_time))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectableValue(
                    value = state.endDate,
                    contentDescription = stringResource(R.string.end_date),
                    onClick = onPickEndDate,
                    modifier = Modifier.weight(1f),
                )
                SelectableValue(
                    value = state.endTime,
                    contentDescription = stringResource(R.string.end_time),
                    onClick = onPickEndTime,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader(stringResource(R.string.repeatings))
            SelectableValue(
                value = state.repeatedLabel.ifEmpty { stringResource(R.string.none) },
                contentDescription = stringResource(R.string.repeatings),
                onClick = onPickRepeated,
            )

            SectionHeader(stringResource(R.string.service))
            SelectableValue(
                value = state.serviceName.ifEmpty { "…" },
                contentDescription = stringResource(R.string.service),
                onClick = onPickService,
            )

            SectionHeader(stringResource(R.string.afterevent))
            DropdownField(
                options = state.afterEventOptions,
                selectedIndex = state.afterEventIndex,
                onSelected = { state.afterEventIndex = it },
                contentDescription = stringResource(R.string.afterevent),
            )

            SectionHeader(stringResource(R.string.location))
            DropdownField(
                options = state.locationOptions,
                selectedIndex = state.locationIndex,
                onSelected = { state.locationIndex = it },
                contentDescription = stringResource(R.string.location),
            )

            SectionHeader(stringResource(R.string.tags))
            SelectableValue(
                value = state.tagsLabel.ifEmpty { "…" },
                contentDescription = stringResource(R.string.tags),
                onClick = onPickTags,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.toggleable(
            value = checked,
            onValueChange = onCheckedChange,
            role = Role.Checkbox,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SelectableValue(
    value: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    contentDescription: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.getOrElse(selectedIndex) { "" }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
