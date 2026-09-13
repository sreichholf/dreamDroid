package net.reichholf.dreamdroid.ui.epg

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Picon

const val EPG_TIME_JUMP_CHIP_TAG = "epg_time_jump_chip"
const val EPG_TIME_JUMP_NOW_TAG = "epg_time_jump_now"
const val EPG_TIME_JUMP_PRIME_TAG = "epg_time_jump_prime"

data class EpgTimeJumpUi(
    val label: String,
    val onPickDateTime: () -> Unit,
    val onNow: () -> Unit,
    val onPrime: () -> Unit,
)

@Composable
fun EpgBouquetScreen(
    items: List<Event>,
    onItemClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollEpoch: Int = 0,
    emptyMessage: String? = null,
    timeJump: EpgTimeJumpUi? = null,
) {
    LaunchedEffect(scrollEpoch) {
        if (scrollEpoch > 0) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (timeJump != null) {
            EpgTimeJumpBar(timeJump)
        }
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (emptyMessage != null) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                items(
                    items,
                    key = { "${it.serviceReference}:${it.eventId}:${it.start}:${it.title}" },
                ) { event ->
                    EpgBouquetRow(
                        event = event,
                        onClick = { onItemClick(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgTimeJumpBar(timeJump: EpgTimeJumpUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AssistChip(
            onClick = timeJump.onPickDateTime,
            label = {
                Text(
                    text = timeJump.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag(EPG_TIME_JUMP_CHIP_TAG),
        )
        TextButton(
            onClick = timeJump.onNow,
            modifier = Modifier.testTag(EPG_TIME_JUMP_NOW_TAG),
        ) {
            Text(stringResource(R.string.now))
        }
        TextButton(
            onClick = timeJump.onPrime,
            modifier = Modifier.testTag(EPG_TIME_JUMP_PRIME_TAG),
        ) {
            Text(stringResource(R.string.epg_prime))
        }
    }
}

@Composable
private fun EpgBouquetRow(
    event: Event,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val piconsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (piconsEnabled) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(57.dp)
                            .height(36.dp),
                        update = { view ->
                            Picon.setPiconForView(
                                context,
                                view,
                                event.serviceReference,
                                event.serviceName,
                                Statics.TAG_PICON,
                                null,
                            )
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = event.serviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = event.startReadable,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = event.durationReadable,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            if (event.descriptionExtended.isNotEmpty()) {
                Text(
                    text = event.descriptionExtended,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}
