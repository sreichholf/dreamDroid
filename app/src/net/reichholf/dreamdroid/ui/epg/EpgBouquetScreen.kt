package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

const val EPG_TIME_JUMP_DATE_CHIP_TAG = "epg_time_jump_date_chip"
const val EPG_TIME_JUMP_TIME_CHIP_TAG = "epg_time_jump_time_chip"
const val EPG_TIME_JUMP_NOW_TAG = "epg_time_jump_now"
const val EPG_TIME_JUMP_PRIME_TAG = "epg_time_jump_prime"
const val EPG_PICK_BOUQUET_CHIP_TAG = "epg_pick_bouquet_chip"

data class EpgTimeJumpUi(
    val dateLabel: String,
    val timeLabel: String,
    val onPickDate: () -> Unit,
    val onPickTime: () -> Unit,
    val onNow: () -> Unit,
    val onPrime: () -> Unit
)

data class EpgBouquetPickUi(val bouquetName: String, val onPickBouquet: () -> Unit)

@Composable
fun EpgBouquetScreen(
    items: List<Event>,
    onItemClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollEpoch: Int = 0,
    emptyMessage: String? = null,
    bouquetPick: EpgBouquetPickUi? = null,
    timeJump: EpgTimeJumpUi? = null
) {
    LaunchedEffect(scrollEpoch) {
        if (scrollEpoch > 0) {
            listState.scrollToItem(0)
        }
    }

    val loadingLabel = stringResource(R.string.loading)
    Column(modifier = modifier.fillMaxSize()) {
        if (bouquetPick != null) {
            EpgBouquetPickBar(bouquetPick)
        }
        if (timeJump != null) {
            EpgTimeJumpBar(timeJump)
        }
        if (items.isEmpty()) {
            ListEmptyState(
                loading = emptyMessage == loadingLabel,
                message = emptyMessage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    items,
                    key = { "${it.serviceReference}:${it.eventId}:${it.start}:${it.title}" }
                ) { event ->
                    EpgBouquetRow(
                        event = event,
                        onClick = { onItemClick(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgBouquetPickBar(pick: EpgBouquetPickUi) {
    val label = pick.bouquetName.ifEmpty { stringResource(R.string.bouquet_overview) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = pick.onPickBouquet,
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_action_list),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EPG_PICK_BOUQUET_CHIP_TAG)
        )
    }
}

@Composable
private fun EpgTimeJumpBar(timeJump: EpgTimeJumpUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssistChip(
            onClick = timeJump.onPickDate,
            label = {
                Text(
                    text = timeJump.dateLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag(EPG_TIME_JUMP_DATE_CHIP_TAG)
        )
        AssistChip(
            onClick = timeJump.onPickTime,
            label = {
                Text(
                    text = timeJump.timeLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier.testTag(EPG_TIME_JUMP_TIME_CHIP_TAG)
        )
        TextButton(
            onClick = timeJump.onNow,
            modifier = Modifier.testTag(EPG_TIME_JUMP_NOW_TAG)
        ) {
            Text(stringResource(R.string.now))
        }
        TextButton(
            onClick = timeJump.onPrime,
            modifier = Modifier.testTag(EPG_TIME_JUMP_PRIME_TAG)
        ) {
            Text(stringResource(R.string.epg_prime))
        }
    }
}

@Composable
private fun EpgBouquetRow(event: Event, onClick: () -> Unit) {
    val context = LocalContext.current
    val piconsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))
    ListRowSurface(modifier = Modifier.clickable(onClick = onClick)) {
        ListItem(
            headlineContent = {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = event.serviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = event.startReadable,
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            },
            leadingContent =
                if (piconsEnabled) {
                    {
                        PiconImage(
                            reference = event.serviceReference,
                            name = event.serviceName,
                            contentDescription = null,
                            modifier = Modifier
                                .width(57.dp)
                                .height(36.dp)
                        )
                    }
                } else {
                    null
                },
            colors = listRowItemColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
