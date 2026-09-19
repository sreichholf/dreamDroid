package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/**
 * Bouquet service INFO/MENU overlay: stream the channel or create a timer from now/next.
 * OK on the service card still streams; this overlay is only opened from INFO/MENU.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvServiceTimerOverlay(
    service: ServiceNowNext,
    onDismiss: () -> Unit,
    onStream: () -> Unit,
    onSetTimer: (Event) -> Unit,
    onEditTimer: (Event) -> Unit,
    modifier: Modifier = Modifier,
    streamingEnabled: Boolean = true,
    mutationsBlocked: Boolean = false
) {
    val firstActionFocus = remember { FocusRequester() }
    var showNeedsReceiver by remember { mutableStateOf(false) }
    var selectedIsNext by remember(service.serviceReference) {
        mutableStateOf(service.now == null && service.next != null)
    }
    val selectedEvent = if (selectedIsNext) service.next else service.now
    BackHandler(onBack = onDismiss)
    LaunchedEffect(service.serviceReference) {
        try {
            firstActionFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Overlay not attached yet.
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusGroup()
            .testTag("tv_service_timer_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = service.serviceName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TvServiceTimerAction(
                label = stringResource(R.string.stream),
                tag = "tv_service_timer_overlay_stream",
                onClick = {
                    if (!streamingEnabled) {
                        showNeedsReceiver = true
                    } else {
                        onStream()
                    }
                },
                focusRequester = firstActionFocus
            )
            val now = service.now
            if (now != null) {
                TvServiceTimerAction(
                    label = eventActionLabel(
                        heading = stringResource(R.string.current_event),
                        event = now
                    ),
                    tag = "tv_service_timer_overlay_current",
                    onClick = { selectedIsNext = false }
                )
            }
            val next = service.next
            if (next != null) {
                TvServiceTimerAction(
                    label = eventActionLabel(
                        heading = stringResource(R.string.next_event),
                        event = next
                    ),
                    tag = "tv_service_timer_overlay_next",
                    onClick = { selectedIsNext = true }
                )
            }
            if (selectedEvent != null) {
                TvServiceTimerAction(
                    label = stringResource(R.string.set_timer),
                    tag = "tv_service_timer_overlay_set_timer",
                    onClick = {
                        if (mutationsBlocked) {
                            showNeedsReceiver = true
                        } else {
                            onSetTimer(selectedEvent)
                        }
                    }
                )
                TvServiceTimerAction(
                    label = stringResource(R.string.edit_timer),
                    tag = "tv_service_timer_overlay_edit_timer",
                    onClick = {
                        if (mutationsBlocked) {
                            showNeedsReceiver = true
                        } else {
                            onEditTimer(selectedEvent)
                        }
                    }
                )
            }
        }
        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(
                onDismiss = { showNeedsReceiver = false },
                testTag = "tv_service_timer_overlay_needs_receiver"
            )
        }
    }
}

private fun eventActionLabel(heading: String, event: Event): String {
    val title = event.title.trim()
    return if (title.isEmpty()) heading else "$heading: $title"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvServiceTimerAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .testTag(tag),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
