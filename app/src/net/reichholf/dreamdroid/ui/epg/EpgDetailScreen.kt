package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.withReadableTimes
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

data class EpgDetailContent(
    val title: String,
    val serviceName: String,
    val description: String,
    val descriptionExtended: String,
    val dateLine: String,
    val isNext: Boolean
)

fun Event.toEpgDetailContent(minutesShort: String): EpgDetailContent? {
    val event = withReadableTimes()
    if (event.title.isEmpty() || event.title == "N/A") return null
    if (event.startReadable.isEmpty()) return null
    val dateLine = "${event.startReadable} (${event.durationReadable} $minutesShort)"
    return EpgDetailContent(
        title = event.title,
        serviceName = event.serviceName,
        description = event.description,
        descriptionExtended = event.descriptionExtended.replace("\\n", "\n"),
        dateLine = dateLine,
        isNext = false
    )
}

/** Keep an EPG sheet open when title/date are empty; show [unavailableTitle] instead. */
fun Event.toEpgDetailContentOrUnavailable(
    minutesShort: String,
    unavailableTitle: String
): EpgDetailContent {
    toEpgDetailContent(minutesShort)?.let { return it }
    val event = withReadableTimes()
    val title = event.title.takeUnless { it.isEmpty() || it == "N/A" } ?: unavailableTitle
    val dateLine = if (event.startReadable.isEmpty()) {
        ""
    } else {
        "${event.startReadable} (${event.durationReadable} $minutesShort)"
    }
    return EpgDetailContent(
        title = title,
        serviceName = event.serviceName,
        description = event.description,
        descriptionExtended = event.descriptionExtended.replace("\\n", "\n"),
        dateLine = dateLine,
        isNext = false
    )
}

@Composable
fun EpgDetailBody(content: EpgDetailContent, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (content.serviceName.isNotEmpty()) {
            Text(
                text = content.serviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (content.description.isNotEmpty()) {
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            text = content.dateLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (content.descriptionExtended.isNotEmpty()) {
            Text(
                text = content.descriptionExtended,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun EpgDetailScreen(
    content: EpgDetailContent,
    onSetTimer: () -> Unit,
    onEditTimer: () -> Unit,
    onImdb: () -> Unit,
    onSimilar: () -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    /** Phone bottom sheet caps body height; TV fullscreen passes null. */
    bodyHeightCap: Dp? = 360.dp
) {
    val status by SessionConnectionHolder.shared.status.collectAsState()
    val timerWritesBlocked = status.blocksMutations
    // Body scrolls; action panel stays pinned like the old XML buttonPanel (when shown).
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bodyHeightCap !=
                        null
                    ) {
                        Modifier.heightIn(max = bodyHeightCap)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            EpgDetailBody(content)
        }
        if (showActions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = onSetTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onlineOnlyLook(timerWritesBlocked)
                ) {
                    Text(stringResource(R.string.set_timer))
                }
                TextButton(
                    onClick = onEditTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onlineOnlyLook(timerWritesBlocked)
                ) {
                    Text(stringResource(R.string.edit_timer))
                }
                TextButton(onClick = onImdb, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.imdb))
                }
                TextButton(onClick = onSimilar, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.similar))
                }
            }
        }
    }
}
