package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

const val TIMER_LIST_STATE_TAG = "timer_list_state"

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
    ListRowSurface(modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ListItem(
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
                colors = listRowItemColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(4.dp)
                        .testTag(TIMER_LIST_STATE_TAG)
                        .background(timerStateColor(item.stateColor))
                )
            }
        }
    }
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
