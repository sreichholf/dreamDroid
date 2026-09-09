package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys

data class EpgDetailContent(
    val title: String,
    val serviceName: String,
    val description: String,
    val descriptionExtended: String,
    val dateLine: String,
    val isNext: Boolean,
)

fun Event.toEpgDetailContent(minutesShort: String): EpgDetailContent? {
    if (title.isEmpty() || title == "N/A") return null
    if (startReadable.isEmpty()) return null
    val dateLine = "$startReadable ($durationReadable $minutesShort)"
    return EpgDetailContent(
        title = title,
        serviceName = serviceName,
        description = description,
        descriptionExtended = descriptionExtended,
        dateLine = dateLine,
        isNext = false,
    )
}

fun ExtendedHashMap.toEpgDetailContent(showNext: Boolean, minutesShort: String): EpgDetailContent? {
    val prefix = if (showNext) EventKeys.PREFIX_NEXT else ""
    val title = getString(prefix + EventKeys.KEY_EVENT_TITLE) ?: "N/A"
    val date = getString(prefix + EventKeys.KEY_EVENT_START_READABLE)
    if (title == "N/A" || date == null) return null
    val duration = getString(prefix + EventKeys.KEY_EVENT_DURATION_READABLE).orEmpty()
    val dateLine = "$date ($duration $minutesShort)"
    return EpgDetailContent(
        title = title,
        serviceName = getString(EventKeys.KEY_SERVICE_NAME).orEmpty(),
        description = getString(prefix + EventKeys.KEY_EVENT_DESCRIPTION, "").orEmpty(),
        descriptionExtended = getString(prefix + EventKeys.KEY_EVENT_DESCRIPTION_EXTENDED).orEmpty(),
        dateLine = dateLine,
        isNext = showNext,
    )
}

@Composable
fun EpgDetailScreen(
    content: EpgDetailContent,
    onSetTimer: () -> Unit,
    onEditTimer: () -> Unit,
    onImdb: () -> Unit,
    onSimilar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (content.serviceName.isNotEmpty()) {
            Text(
                text = content.serviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (content.description.isNotEmpty()) {
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = content.dateLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (content.descriptionExtended.isNotEmpty()) {
            Text(
                text = content.descriptionExtended,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSetTimer, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.set_timer))
        }
        TextButton(onClick = onEditTimer, modifier = Modifier.fillMaxWidth()) {
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
