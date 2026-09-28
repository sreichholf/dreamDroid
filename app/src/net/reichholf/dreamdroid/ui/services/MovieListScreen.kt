package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

enum class MovieRowAction(override val label: Int) : RowMenuAction {
    Info(R.string.epg),
    Zap(R.string.zap),
    Delete(R.string.delete),
    Download(R.string.download),
    Stream(R.string.stream)
}

@Composable
fun MovieListScreen(
    items: List<MovieListItem>,
    onItemClick: (MovieListItem) -> Unit,
    onItemLongClick: (MovieListItem) -> Unit,
    modifier: Modifier = Modifier,
    menu: RowMenuState<MovieRowAction>? = null,
    onMenuAction: (MovieRowAction) -> Unit = {},
    onMenuDismiss: () -> Unit = {}
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { it.index }) { item ->
            Box {
                MovieRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) }
                )
                RowMenu(item.index, menu, onMenuAction, onMenuDismiss)
            }
        }
    }
}

@Composable
private fun MovieRow(item: MovieListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListRowSurface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = item.serviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = listOf(item.time, item.length, item.fileSize).filter {
                            it.isNotEmpty()
                        }.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = listRowItemColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
