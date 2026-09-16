package net.reichholf.dreamdroid.ui.services

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
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowAnchoredClickable
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

typealias MovieListTap = (item: MovieListItem, windowX: Int, windowY: Int) -> Unit

@Composable
fun MovieListScreen(
    items: List<MovieListItem>,
    onItemClick: MovieListTap,
    onItemLongClick: MovieListTap,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { it.index }) { item ->
            MovieRow(
                item = item,
                onClick = { x, y -> onItemClick(item, x, y) },
                onLongClick = { x, y -> onItemLongClick(item, x, y) }
            )
        }
    }
}

@Composable
private fun MovieRow(
    item: MovieListItem,
    onClick: (windowX: Int, windowY: Int) -> Unit,
    onLongClick: (windowX: Int, windowY: Int) -> Unit
) {
    ListRowSurface(
        modifier = Modifier.listRowAnchoredClickable(onClick = onClick, onLongClick = onLongClick)
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
