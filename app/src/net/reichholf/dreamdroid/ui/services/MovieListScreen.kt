package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

typealias MovieListTap = (item: MovieListItem, windowX: Int, windowY: Int) -> Unit

@Composable
fun MovieListScreen(
    items: List<MovieListItem>,
    onItemClick: MovieListTap,
    onItemLongClick: MovieListTap,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        items(items, key = { it.index }) { item ->
            MovieRow(
                item = item,
                onClick = { x, y -> onItemClick(item, x, y) },
                onLongClick = { x, y -> onItemLongClick(item, x, y) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieRow(
    item: MovieListItem,
    onClick: (windowX: Int, windowY: Int) -> Unit,
    onLongClick: (windowX: Int, windowY: Int) -> Unit,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun windowTopLeft(): Pair<Int, Int> {
        val bounds: Rect = coords?.boundsInWindow() ?: return 0 to 0
        return bounds.left.roundToInt() to bounds.top.roundToInt()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coords = it }
            .combinedClickable(
                onClick = {
                    val (x, y) = windowTopLeft()
                    onClick(x, y)
                },
                onLongClick = {
                    val (x, y) = windowTopLeft()
                    onLongClick(x, y)
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.serviceName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOf(item.time, item.length, item.fileSize).filter { it.isNotEmpty() }.joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
