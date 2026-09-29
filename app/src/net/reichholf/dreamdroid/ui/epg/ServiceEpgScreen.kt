package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

@Composable
fun ServiceEpgScreen(
    sections: List<EpgDaySection>,
    onItemClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null
) {
    val loadingLabel = stringResource(R.string.loading)
    if (sections.isEmpty()) {
        ListEmptyState(
            loading = emptyMessage == loadingLabel,
            message = emptyMessage,
            modifier = modifier
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        sections.forEachIndexed { index, section ->
            val day = section.day
            if (day != null) {
                stickyHeader(key = "day:$index") { EpgDayHeader(day) }
            }
            itemsIndexed(
                section.events,
                key = { _, it -> "${it.eventId}:${it.start}:${it.title}" }
            ) { eventIndex, event ->
                ServiceEpgRow(
                    event = event,
                    onClick = { onItemClick(event) },
                    showDate = day == null || eventIndex < section.earlierStarts
                )
            }
        }
    }
}

@Composable
private fun ServiceEpgRow(event: Event, onClick: () -> Unit, showDate: Boolean) {
    ListRowSurface(modifier = Modifier.clickable(onClick = onClick)) {
        ListItem(
            headlineContent = {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = if (showDate || event.startTimeReadable.isEmpty()) {
                                event.startReadable
                            } else {
                                event.startTimeReadable
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = event.durationReadable,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (event.descriptionExtended.isNotEmpty()) {
                        Text(
                            text = event.descriptionExtended,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            },
            colors = listRowItemColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
