package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.compose.listRowItemColors
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.TextInputDialog
import net.reichholf.dreamdroid.ui.text.asString
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

const val BOUQUET_DRAG_HANDLE_TAG = "bouquet_drag_handle"

/**
 * TV / Radio switch above the bouquet index. A drag moves rows here; [onMove] gets the
 * dropped row's final 0-based position once, on drop. [onOpenBouquet] makes rows clickable.
 */
@Composable
fun BouquetListScreen(
    state: BouquetListUiState,
    onModeChange: (BouquetMode) -> Unit,
    onRefresh: () -> Unit,
    onMenu: (BouquetEntry) -> Unit,
    onMenuAction: (BouquetEntry, BouquetRowAction) -> Unit,
    onMenuDismiss: () -> Unit,
    onMove: (ref: String, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenBouquet: ((BouquetEntry) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        ModeSwitch(
            mode = state.mode,
            enabled = !state.pending,
            onModeChange = onModeChange
        )
        Box(modifier = Modifier.weight(1f)) {
            DreamDroidPullRefresh(
                refreshing = state.refreshing,
                onRefresh = onRefresh,
                enabled = !state.pending
            ) {
                when (val content = state.content) {
                    BouquetListContent.Loading -> ListEmptyState(loading = true, message = null)

                    BouquetListContent.NotInstalled -> ListEmptyState(
                        loading = false,
                        message = stringResource(R.string.bouquet_editor_not_installed),
                        onRetry = onRefresh
                    )

                    is BouquetListContent.Failed -> ListEmptyState(
                        loading = false,
                        message = content.message.asString(),
                        onRetry = onRefresh
                    )

                    is BouquetListContent.Ready -> if (content.bouquets.isEmpty()) {
                        ListEmptyState(
                            loading = false,
                            message = stringResource(R.string.no_list_item)
                        )
                    } else {
                        BouquetRows(
                            bouquets = content.bouquets,
                            state = state,
                            onMenu = onMenu,
                            onMenuAction = onMenuAction,
                            onMenuDismiss = onMenuDismiss,
                            onMove = onMove,
                            onOpenBouquet = onOpenBouquet
                        )
                    }
                }
            }
            if (state.pending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The add, rename, and remove dialogs; [nameState] is the ViewModel's name field. */
@Composable
fun BouquetListDialogs(
    state: BouquetListUiState,
    nameState: TextFieldState,
    onConfirmAdd: () -> Unit,
    onConfirmRename: () -> Unit,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val nameError = state.nameError?.asString()
    when (val dialog = state.dialog) {
        null -> Unit

        BouquetListDialog.Add -> TextInputDialog(
            title = stringResource(R.string.bouquet_add),
            state = nameState,
            onDismiss = onDismiss,
            onConfirm = onConfirmAdd,
            error = nameError
        )

        is BouquetListDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.bouquet_rename),
            state = nameState,
            onDismiss = onDismiss,
            onConfirm = onConfirmRename,
            error = nameError
        )

        is BouquetListDialog.Remove -> ConfirmAlertDialog(
            title = stringResource(R.string.bouquet_remove),
            message = stringResource(R.string.bouquet_remove_confirm, dialog.bouquet.name),
            onDismiss = onDismiss,
            onConfirm = onConfirmRemove,
            confirmLabel = stringResource(R.string.remove),
            destructive = true
        )
    }
}

@Composable
private fun ModeSwitch(mode: BouquetMode, enabled: Boolean, onModeChange: (BouquetMode) -> Unit) {
    val modes = listOf(
        BouquetMode.Tv to stringResource(R.string.tv),
        BouquetMode.Radio to stringResource(R.string.radio)
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ListRowHorizontalInset, vertical = 8.dp)
    ) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun BouquetRows(
    bouquets: List<BouquetEntry>,
    state: BouquetListUiState,
    onMenu: (BouquetEntry) -> Unit,
    onMenuAction: (BouquetEntry, BouquetRowAction) -> Unit,
    onMenuDismiss: () -> Unit,
    onMove: (ref: String, position: Int) -> Unit,
    onOpenBouquet: ((BouquetEntry) -> Unit)?
) {
    // The drag reorders this copy; the ViewModel hears of the drop only.
    var rows by remember(bouquets) { mutableStateOf(bouquets) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.reference }) { bouquet ->
            ReorderableItem(reorderState, key = bouquet.reference) {
                val index = rows.indexOf(bouquet)
                Box(
                    modifier = Modifier.semantics {
                        if (state.editable) {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) {
                                    onMove(bouquet.reference, index - 1)
                                    true
                                }.takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) {
                                    onMove(bouquet.reference, index + 1)
                                    true
                                }.takeIf { index < rows.lastIndex }
                            )
                        }
                    }
                ) {
                    BouquetRow(
                        bouquet = bouquet,
                        onClick = onOpenBouquet?.let { open -> { open(bouquet) } },
                        onMenu = { onMenu(bouquet) },
                        dragHandle = {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.bouquet_drag),
                                modifier = Modifier
                                    .draggableHandle(
                                        enabled = state.editable,
                                        onDragStopped = {
                                            val position = rows.indexOfFirst {
                                                it.reference == bouquet.reference
                                            }
                                            onMove(bouquet.reference, position)
                                        }
                                    )
                                    .testTag(BOUQUET_DRAG_HANDLE_TAG)
                                    .padding(12.dp)
                            )
                        }
                    )
                    RowMenu(
                        rowKey = bouquet.reference,
                        state = state.menu,
                        onAction = { onMenuAction(bouquet, it) },
                        onDismiss = onMenuDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun BouquetRow(
    bouquet: BouquetEntry,
    onClick: (() -> Unit)?,
    onMenu: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    ListRowSurface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = bouquet.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    dragHandle()
                }
            },
            colors = listRowItemColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
