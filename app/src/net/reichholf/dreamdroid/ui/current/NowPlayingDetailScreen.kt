package net.reichholf.dreamdroid.ui.current

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.epg.EpgDetailBody
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContent
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

/**
 * EPG-style now/next body with a single Stream action (no timer / IMDb / similar).
 */
@Composable
fun NowPlayingDetailScreen(
    current: CurrentService?,
    onStream: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val minutesShort = stringResource(R.string.minutes_short)
    val serviceName = current?.service?.name.orEmpty()
    val nowContent = current?.now
        ?.withServiceName(serviceName)
        ?.toEpgDetailContent(minutesShort)
    val nextContent = current?.next
        ?.withServiceName("")
        ?.toEpgDetailContent(minutesShort)
    val canStream = currentServiceCanStream(current)
    val streamBlocked = SessionConnectionHolder.shared.status.collectAsState().value.blocksMutations
    val emptyTitle = when {
        loading && current == null -> stringResource(R.string.loading)
        else -> serviceName.ifEmpty { stringResource(R.string.not_available) }
    }

    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            if (nowContent != null) {
                EpgDetailBody(nowContent)
            } else {
                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (nextContent != null) {
                Text(
                    text = stringResource(R.string.next),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                EpgDetailBody(nextContent)
            }
        }
        if (canStream) {
            Button(
                onClick = onStream,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .onlineOnlyLook(streamBlocked)
            ) {
                Text(stringResource(R.string.stream_current))
            }
        }
    }
}

private fun Event.withServiceName(serviceName: String): Event {
    if (this.serviceName == serviceName) {
        return this
    }
    return copy(serviceName = serviceName)
}
