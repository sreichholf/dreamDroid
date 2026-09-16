package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

/**
 * Phase 2.1g-ii-e: Material 3 choice / progress dialogs in composition
 * (replaces MultiChoiceDialog / SimpleChoiceDialog / IndeterminateProgress DialogFragments).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BasicAlertDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier,
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            contentColor = AlertDialogDefaults.textContentColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                content = content
            )
        }
    }
}

@Composable
fun MultiChoiceAlertDialog(
    title: String,
    items: List<String>,
    initialChecked: BooleanArray,
    onDismiss: () -> Unit,
    onConfirm: (selectedIndices: List<Int>) -> Unit,
    confirmLabel: String = stringResource(R.string.ok)
) {
    val checked = remember(items, initialChecked) {
        mutableStateListOf(
            *BooleanArray(items.size) { i ->
                i < initialChecked.size && initialChecked[i]
            }.toTypedArray()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (items.isEmpty()) {
                    Text(stringResource(R.string.no_list_item))
                }
                items.forEachIndexed { index, label ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            Checkbox(
                                checked = checked[index],
                                onCheckedChange = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = checked[index],
                                role = Role.Checkbox,
                                onValueChange = { checked[index] = it }
                            )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = checked.mapIndexedNotNull { i, on -> if (on) i else null }
                    onConfirm(selected)
                    onDismiss()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SimpleChoiceAlertDialog(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    onChoice: (index: Int) -> Unit
) {
    BasicAlertDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = false,
                            role = Role.RadioButton,
                            onClick = {
                                onChoice(index)
                                onDismiss()
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = false, onClick = null)
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/** In-composition stand-in for a blocking [android.app.ProgressDialog]. */
data class IndeterminateProgressState(val message: String, val title: String = "")

@Composable
fun IndeterminateProgressDialog(title: String, message: String, onDismiss: () -> Unit = {}) {
    BasicAlertDialogSurface(onDismissRequest = onDismiss) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        BackHandler(onBack = onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
            Text(message)
        }
    }
}

@Composable
fun IndeterminateProgressHost(progress: IndeterminateProgressState?) {
    var dismissed by remember(progress) { mutableStateOf(false) }
    val current = progress.takeUnless { dismissed }
    // AlertDialog is a separate window that pauses the activity. Handle activity-level
    // Back here so it dismisses the spinner instead of finishing the host.
    BackHandler(enabled = current != null) {
        dismissed = true
    }
    if (current != null) {
        IndeterminateProgressDialog(
            title = current.title,
            message = current.message,
            onDismiss = { dismissed = true }
        )
    }
}

@Composable
fun ConfirmAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = stringResource(R.string.ok),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
