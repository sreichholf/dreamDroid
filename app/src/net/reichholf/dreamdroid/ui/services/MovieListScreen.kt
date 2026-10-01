package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState

/** [onlineOnly] actions need the receiver as soon as they are picked. */
enum class MovieRowAction(override val label: Int, val onlineOnly: Boolean) : RowMenuAction {
    Info(R.string.epg, onlineOnly = false),
    Zap(R.string.zap, onlineOnly = true),

    /** Asks first; the confirmed delete needs the receiver. */
    Delete(R.string.delete, onlineOnly = false),
    Download(R.string.download, onlineOnly = true),
    Stream(R.string.stream, onlineOnly = true)
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
    ListRow(
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
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}
