package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

/**
 * Phase 2.1g-ii-e: Material 3 choice / progress dialogs in composition
 * (replaces MultiChoiceDialog / SimpleChoiceDialog / IndeterminateProgress DialogFragments).
 */

@Composable
fun MultiChoiceAlertDialog(
    title: String,
    items: List<String>,
    initialChecked: BooleanArray,
    onDismiss: () -> Unit,
    onConfirm: (selectedIndices: List<Int>) -> Unit,
    confirmLabel: String = stringResource(R.string.ok),
) {
    val checked = remember(items, initialChecked) {
        mutableStateListOf(*BooleanArray(items.size) { i ->
            i < initialChecked.size && initialChecked[i]
        }.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked[index],
                                role = Role.Checkbox,
                                onValueChange = { checked[index] = it },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked[index],
                            onCheckedChange = null,
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = checked.mapIndexedNotNull { i, on -> if (on) i else null }
                    onConfirm(selected)
                    onDismiss()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SimpleChoiceAlertDialog(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    onChoice: (index: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChoice(index)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun IndeterminateProgressDialog(
    title: String,
    message: String,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                Text(message)
            }
        },
        confirmButton = {},
    )
}
