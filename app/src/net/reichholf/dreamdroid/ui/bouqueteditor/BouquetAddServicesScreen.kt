package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.text.asString

/**
 * The sources, then a source's folders and services. Services the bouquet has already
 * show checked and cannot be picked.
 */
@Composable
fun BouquetAddServicesScreen(
    state: BouquetAddServicesUiState,
    onSource: (ServiceSource) -> Unit,
    onFolder: (BouquetEntry) -> Unit,
    onToggle: (BouquetEntry) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.source == null) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(ServiceSource.entries) { source ->
                    PlainRow(text = stringResource(source.label), onClick = { onSource(source) })
                }
            }
        } else {
            when (val content = state.content) {
                AddServicesList.Loading -> ListEmptyState(loading = true, message = null)

                is AddServicesList.Failed -> ListEmptyState(
                    loading = false,
                    message = content.message.asString(),
                    onRetry = onRetry
                )

                is AddServicesList.Ready -> if (content.entries.isEmpty()) {
                    ListEmptyState(loading = false, message = stringResource(R.string.no_list_item))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(content.entries) { entry ->
                            if (entry.kind == BouquetEntryKind.Directory) {
                                PlainRow(text = entry.name, onClick = { onFolder(entry) })
                            } else {
                                ServiceRow(
                                    entry = entry,
                                    present = !state.selectable(entry),
                                    checked = entry.reference in state.selected,
                                    enabled = !state.pending,
                                    onToggle = { onToggle(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.pending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlainRow(text: String, onClick: () -> Unit) {
    ListRow(
        headlineContent = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ServiceRow(
    entry: BouquetEntry,
    present: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ListRow(
        leadingContent = {
            Checkbox(
                checked = checked || present,
                onCheckedChange = null,
                enabled = enabled && !present
            )
        },
        headlineContent = {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = if (present) {
            { Text(stringResource(R.string.bouquet_service_present)) }
        } else {
            null
        },
        modifier = Modifier
            .toggleable(
                value = checked || present,
                enabled = enabled && !present,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
    )
}
