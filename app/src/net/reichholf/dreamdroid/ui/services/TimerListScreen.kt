package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.compose.ListRow

private val TimerStateBarWidth = 4.dp

@Composable
fun TimerListScreen(
    items: List<TimerListItem>,
    onItemClick: (TimerListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { it.index }) { item ->
            TimerRow(item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun TimerRow(item: TimerListItem, onClick: () -> Unit) {
    val stateColor = timerStateColor(item.stateColor)
    ListRow(
        headlineContent = {
            Text(
                text = item.name,
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
                    text = "${item.begin} – ${item.end}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${item.action}  ${item.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        modifier = Modifier
            .drawWithContent {
                drawContent()
                drawRect(stateColor, size = Size(TimerStateBarWidth.toPx(), size.height))
            }
    )
}

@Composable
private fun timerStateColor(stateId: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (stateId) {
        0 -> scheme.tertiary
        1 -> scheme.error
        2 -> scheme.primary
        3 -> scheme.primary
        else -> scheme.outline
    }
}
