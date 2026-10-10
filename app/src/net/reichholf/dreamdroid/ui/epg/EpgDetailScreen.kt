package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.withReadableTimes
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

const val EPG_DETAIL_CAPPED_TAG = "epg_detail_capped"
const val EPG_DETAIL_UNCAPPED_TAG = "epg_detail_uncapped"

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
fun EpgDetailBody(
    content: EpgDetailContent,
    modifier: Modifier = Modifier,
    /** Icon buttons at the end of the title row. */
    titleActions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            titleActions()
        }
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
    /** The session blocks timer writes: the timer actions look online-only. */
    timerWritesBlocked: Boolean = false,
    /** Phone bottom sheet caps body height; TV fullscreen and list-detail panes pass null. */
    bodyHeightCap: Dp? = 360.dp,
    /** Opens a new AutoTimer for this event; null hides the action. */
    onRecordSeries: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bodyTag = if (bodyHeightCap == null) EPG_DETAIL_UNCAPPED_TAG else EPG_DETAIL_CAPPED_TAG
        val body = @Composable { bodyModifier: Modifier ->
            Column(
                bodyModifier
                    .fillMaxWidth()
                    .testTag(bodyTag)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                EpgDetailBody(
                    content = content,
                    titleActions = {
                        if (showActions) {
                            IconButton(onClick = onSimilar) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_action_search),
                                    contentDescription = stringResource(R.string.similar)
                                )
                            }
                            IconButton(onClick = onImdb) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_menu_movie),
                                    contentDescription = stringResource(R.string.imdb)
                                )
                            }
                        }
                    }
                )
            }
        }
        val actions = @Composable {
            EpgDetailActions(onSetTimer, onEditTimer, onRecordSeries, timerWritesBlocked)
        }
        val bounded = constraints.hasBoundedHeight
        // One state for both layouts keeps the reading position when the height crosses over.
        val scrollState = rememberScrollState()
        if (!showActions || !bounded || maxHeight >= MinHeightForPinnedActions) {
            // The body scrolls; the actions stay pinned below it.
            Column(Modifier.fillMaxWidth()) {
                body(
                    Modifier
                        .then(bodyHeightCap?.let { Modifier.heightIn(max = it) } ?: Modifier)
                        // Leaves the actions their room in a bounded pane or sheet.
                        .then(if (bounded) Modifier.weight(1f, fill = false) else Modifier)
                        .verticalScroll(scrollState)
                )
                if (showActions) actions()
            }
        } else {
            // Too short to pin the actions (a short pane or sheet): everything scrolls together.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                body(Modifier)
                actions()
            }
        }
    }
}

/** Below this height the actions scroll with the body instead of taking most of the space. */
private val MinHeightForPinnedActions = 320.dp

@Composable
private fun EpgDetailActions(
    onSetTimer: () -> Unit,
    onEditTimer: () -> Unit,
    onRecordSeries: (() -> Unit)?,
    timerWritesBlocked: Boolean
) {
    // One row where the labels fit, wrapped otherwise; each button takes an equal share.
    // FlowRow breaks lines for weighted items by their min intrinsic width (the longest
    // word); IntrinsicSize.Max makes that the whole one-line label.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSetTimer,
            modifier = Modifier
                .weight(1f)
                .width(IntrinsicSize.Max)
                .onlineOnlyLook(timerWritesBlocked)
        ) {
            ActionLabel(stringResource(R.string.set_timer))
        }
        OutlinedButton(
            onClick = onEditTimer,
            modifier = Modifier
                .weight(1f)
                .width(IntrinsicSize.Max)
                .onlineOnlyLook(timerWritesBlocked)
        ) {
            ActionLabel(stringResource(R.string.edit_timer))
        }
        if (onRecordSeries != null) {
            OutlinedButton(
                onClick = onRecordSeries,
                modifier = Modifier
                    .weight(1f)
                    .width(IntrinsicSize.Max)
                    .onlineOnlyLook(timerWritesBlocked)
            ) {
                ActionLabel(stringResource(R.string.autotimer_record_series))
            }
        }
    }
}

@Composable
private fun ActionLabel(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
