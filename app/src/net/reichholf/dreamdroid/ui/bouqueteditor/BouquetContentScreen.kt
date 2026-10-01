package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.TextInputDialog
import net.reichholf.dreamdroid.ui.text.asString
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The entries of one bouquet. Markers read as section headings; alternatives groups show
 * but are edited on the receiver. A drag moves rows here; [onMove] gets the dropped row's
 * final 0-based position once, on drop.
 */
@Composable
fun BouquetContentScreen(
    state: BouquetContentUiState,
    onRefresh: () -> Unit,
    onMenu: (BouquetContentRow) -> Unit,
    onMenuAction: (BouquetContentRow, BouquetEntryAction) -> Unit,
    onMenuDismiss: () -> Unit,
    onMove: (key: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        DreamDroidPullRefresh(
            refreshing = state.refreshing,
            onRefresh = onRefresh,
            enabled = !state.pending
        ) {
            when (val content = state.content) {
                BouquetContentList.Loading -> ListEmptyState(loading = true, message = null)

                is BouquetContentList.Failed -> ListEmptyState(
                    loading = false,
                    message = content.message.asString(),
                    onRetry = onRefresh
                )

                is BouquetContentList.Ready -> if (content.rows.isEmpty()) {
                    ListEmptyState(loading = false, message = stringResource(R.string.no_list_item))
                } else {
                    EntryRows(
                        rows = content.rows,
                        state = state,
                        onMenu = onMenu,
                        onMenuAction = onMenuAction,
                        onMenuDismiss = onMenuDismiss,
                        onMove = onMove
                    )
                }
            }
        }
        if (state.pending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The rename, marker, and remove dialogs; [nameState] is the ViewModel's name field. */
@Composable
fun BouquetContentDialogs(
    state: BouquetContentUiState,
    nameState: TextFieldState,
    onConfirmRename: () -> Unit,
    onConfirmAddMarker: () -> Unit,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val nameError = state.nameError?.asString()
    when (val dialog = state.dialog) {
        null -> Unit

        is BouquetContentDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.rename),
            state = nameState,
            onDismiss = onDismiss,
            onConfirm = onConfirmRename,
            error = nameError
        )

        is BouquetContentDialog.AddMarker -> TextInputDialog(
            title = stringResource(R.string.bouquet_marker_add),
            state = nameState,
            onDismiss = onDismiss,
            onConfirm = onConfirmAddMarker,
            error = nameError
        )

        is BouquetContentDialog.Remove -> ConfirmAlertDialog(
            title = stringResource(R.string.bouquet_entry_remove),
            message = stringResource(R.string.bouquet_entry_remove_confirm, dialog.row.entry.name),
            onDismiss = onDismiss,
            onConfirm = onConfirmRemove,
            confirmLabel = stringResource(R.string.remove),
            destructive = true
        )
    }
}

@Composable
private fun EntryRows(
    rows: List<BouquetContentRow>,
    state: BouquetContentUiState,
    onMenu: (BouquetContentRow) -> Unit,
    onMenuAction: (BouquetContentRow, BouquetEntryAction) -> Unit,
    onMenuDismiss: () -> Unit,
    onMove: (key: Int, position: Int) -> Unit
) {
    // The drag reorders this copy; the ViewModel hears of the drop only.
    var shown by remember(rows) { mutableStateOf(rows) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        shown = shown.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(shown, key = { it.key }) { row ->
            ReorderableItem(reorderState, key = row.key) {
                val index = shown.indexOf(row)
                Box(
                    modifier = Modifier.semantics {
                        if (state.editable) {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) {
                                    onMove(row.key, index - 1)
                                    true
                                }.takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) {
                                    onMove(row.key, index + 1)
                                    true
                                }.takeIf { index < shown.lastIndex }
                            )
                        }
                    }
                ) {
                    val trailing: @Composable () -> Unit = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onMenu(row) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.bouquet_drag),
                                modifier = Modifier
                                    .draggableHandle(
                                        enabled = state.editable,
                                        onDragStopped = {
                                            val position = shown.indexOfFirst {
                                                it.key == row.key
                                            }
                                            onMove(row.key, position)
                                            // An accepted move shows in the next list.
                                            shown = rows
                                        }
                                    )
                                    .testTag(BOUQUET_DRAG_HANDLE_TAG)
                                    .padding(12.dp)
                            )
                        }
                    }
                    if (row.entry.kind == BouquetEntryKind.Marker) {
                        MarkerRow(name = row.entry.name, trailing = trailing)
                    } else {
                        EntryRow(row = row, trailing = trailing)
                    }
                    RowMenu(
                        rowKey = row.key,
                        state = state.menu,
                        onAction = { onMenuAction(row, it) },
                        onDismiss = onMenuDismiss
                    )
                }
            }
        }
    }
}

/** A marker, styled like a section heading of the list. */
@Composable
private fun MarkerRow(name: String, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ListRowHorizontalInset, top = 8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        trailing()
    }
}

@Composable
private fun EntryRow(row: BouquetContentRow, trailing: @Composable () -> Unit) {
    val note = when (row.entry.kind) {
        BouquetEntryKind.Stream -> R.string.bouquet_entry_stream
        BouquetEntryKind.Alternative -> R.string.bouquet_entry_alternative
        else -> null
    }
    ListRow(
        headlineContent = {
            Text(
                text = row.entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = note?.let { { Text(stringResource(it)) } },
        trailingContent = trailing
    )
}
