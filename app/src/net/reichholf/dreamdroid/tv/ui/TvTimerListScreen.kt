package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.services.TimerListItem
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/** D-pad TV timer list: New Timer, then enable / edit / delete per timer. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerListScreen(
    items: List<TimerListItem>,
    onAdd: () -> Unit,
    onToggleEnabled: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDeleteConfirmed: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<TimerListItem?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_timers_list")
    ) {
        item(key = "add") {
            Surface(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .testTag("tv_timers_add"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.new_timer),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        items(items, key = { it.index }) { item ->
            TvTimerRow(
                item = item,
                onToggleEnabled = { onToggleEnabled(item.index) },
                onEdit = { onEdit(item.index) },
                onDelete = {
                    onDelete(item.index)
                    pendingDelete = item
                }
            )
        }
    }

    pendingDelete?.let { item ->
        ConfirmAlertDialog(
            title = item.name,
            message = stringResource(R.string.delete_confirm),
            onDismiss = { pendingDelete = null },
            onConfirm = { onDeleteConfirmed(item.index) },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTimerRow(
    item: TimerListItem,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggleEnabled,
            modifier = Modifier
                .weight(1f)
                .height(96.dp)
                .testTag("tv_timers_row_${item.index}")
                .semantics { selected = item.enabled },
            colors = tvTimersEnableCardColors(item.enabled),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = item.serviceName,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${item.begin} – ${item.end}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Surface(
            onClick = onEdit,
            modifier = Modifier
                .width(72.dp)
                .height(96.dp)
                .testTag("tv_timers_edit_${item.index}"),
            colors = dreamDroidTvCardColors(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_edit),
                    contentDescription = stringResource(R.string.edit)
                )
            }
        }
        Surface(
            onClick = onDelete,
            modifier = Modifier
                .width(72.dp)
                .height(96.dp)
                .testTag("tv_timers_delete_${item.index}"),
            colors = dreamDroidTvCardColors(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_delete),
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

/**
 * Enabled timer activate-card: [dreamDroidTvCardColors] with unfocused container
 * [PhoneMaterialTheme.colorScheme.secondaryContainer]. Focused stays inverse.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun tvTimersEnableCardColors(enabled: Boolean): ClickableSurfaceColors {
    if (!enabled) {
        return dreamDroidTvCardColors()
    }
    val phone = PhoneMaterialTheme.colorScheme
    return ClickableSurfaceDefaults.colors(
        containerColor = phone.secondaryContainer,
        contentColor = phone.onSurface,
        focusedContainerColor = phone.inverseSurface,
        focusedContentColor = phone.inverseOnSurface,
        pressedContainerColor = phone.inverseSurface,
        pressedContentColor = phone.inverseOnSurface
    )
}
