package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.TextStyle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.text.asString

/**
 * The AutoTimer list with pull to refresh. A tap on a row opens its preview; its switch
 * enables or pauses it; its menu offers the other actions.
 */
@Composable
fun AutoTimerListScreen(
    state: AutoTimerListUiState,
    onRefresh: () -> Unit,
    onOpen: (AutoTimerEntry.Readable) -> Unit,
    onEnabledChange: (AutoTimerEntry.Readable, Boolean) -> Unit,
    onMenu: (AutoTimerEntry) -> Unit,
    onMenuAction: (AutoTimerEntry, AutoTimerRowAction) -> Unit,
    onMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        DreamDroidPullRefresh(
            refreshing = state.refreshing,
            onRefresh = onRefresh,
            enabled = !state.pending && !state.running
        ) {
            when (val content = state.content) {
                AutoTimerListContent.Loading -> ListEmptyState(loading = true, message = null)

                AutoTimerListContent.PluginMissing -> ListEmptyState(
                    loading = false,
                    message = stringResource(R.string.autotimer_not_installed),
                    onRetry = onRefresh
                )

                is AutoTimerListContent.Failed -> ListEmptyState(
                    loading = false,
                    message = content.message.asString(),
                    onRetry = onRefresh
                )

                is AutoTimerListContent.Ready -> if (content.entries.isEmpty()) {
                    ListEmptyState(
                        loading = false,
                        message = stringResource(R.string.autotimer_empty)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(content.entries, key = { it.id.value }) { entry ->
                            Box {
                                AutoTimerRow(
                                    entry = entry,
                                    writable = !state.pending && !state.running &&
                                        !state.refreshing,
                                    onOpen = onOpen,
                                    onEnabledChange = onEnabledChange,
                                    onMenu = { onMenu(entry) }
                                )
                                RowMenu(
                                    rowKey = entry.id.value,
                                    state = state.menu,
                                    onAction = { onMenuAction(entry, it) },
                                    onDismiss = onMenuDismiss
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.pending || state.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The delete and Run now confirmations, and the result of a run. */
@Composable
fun AutoTimerListDialogs(
    state: AutoTimerListUiState,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmRun: () -> Unit,
    onDismissRun: () -> Unit,
    onRunResultShown: () -> Unit
) {
    val entry = state.deleting
    val runResult = state.runResult
    when {
        entry != null -> ConfirmAlertDialog(
            title = stringResource(R.string.autotimer_delete),
            message = stringResource(R.string.autotimer_delete_confirm, entry.title()),
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete,
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )

        state.confirmRun -> ConfirmAlertDialog(
            title = stringResource(R.string.autotimer_run),
            message = stringResource(R.string.autotimer_run_confirm),
            onDismiss = onDismissRun,
            onConfirm = onConfirmRun,
            confirmLabel = stringResource(R.string.autotimer_run)
        )

        runResult != null -> AlertDialog(
            onDismissRequest = onRunResultShown,
            title = { Text(stringResource(R.string.autotimer_run)) },
            text = { Text(runResult.asString()) },
            confirmButton = {
                TextButton(onClick = onRunResultShown) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
private fun AutoTimerRow(
    entry: AutoTimerEntry,
    writable: Boolean,
    onOpen: (AutoTimerEntry.Readable) -> Unit,
    onEnabledChange: (AutoTimerEntry.Readable, Boolean) -> Unit,
    onMenu: () -> Unit
) {
    val lines = when (entry) {
        is AutoTimerEntry.Readable -> summaryLines(entry.autoTimer.settings)
        is AutoTimerEntry.Unreadable -> listOf(stringResource(R.string.autotimer_unreadable))
    }
    ListRow(
        headlineContent = {
            Text(
                text = entry.title(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Column {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry is AutoTimerEntry.Readable) {
                    Switch(
                        checked = entry.autoTimer.settings.enabled,
                        onCheckedChange = { onEnabledChange(entry, it) },
                        enabled = writable,
                        // Read with its on/off state, so it names the AutoTimer.
                        modifier = Modifier.semantics {
                            contentDescription = entry.title()
                        }
                    )
                }
                IconButton(onClick = onMenu, enabled = writable) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
            }
        },
        onClick = if (entry is AutoTimerEntry.Readable) {
            { onOpen(entry) }
        } else {
            null
        }
    )
}

/** The box fills an empty name with the match; an unreadable entry may have neither. */
private fun AutoTimerEntry.title(): String = when (this) {
    is AutoTimerEntry.Readable -> name.ifBlank { autoTimer.settings.match }
    is AutoTimerEntry.Unreadable -> name
}

/** What, where, and when: match and targets, then the time window and days. */
@Composable
internal fun summaryLines(settings: AutoTimerSettings): List<String> {
    val what = listOf(
        stringResource(R.string.autotimer_match, settings.match),
        targetsSummary(settings.targets)
    ).joinToString(SEPARATOR)
    val clock = rememberClockFormat()
    val whenParts = listOfNotNull(
        settings.timeWindow?.let { "${it.from.format(clock)}–${it.to.format(clock)}" },
        daysSummary(settings.include.days),
        stringResource(R.string.autotimer_paused).takeUnless { settings.enabled }
    )
    return listOfNotNull(what, whenParts.joinToString(SEPARATOR).ifEmpty { null })
}

@Composable
private fun targetsSummary(targets: List<Target>): String {
    if (targets.isEmpty()) {
        return stringResource(R.string.autotimer_all_channels)
    }
    val shown = targets.take(SHOWN_TARGETS).joinToString(", ") { it.name.ifBlank { it.ref } }
    val more = targets.size - SHOWN_TARGETS
    return if (more > 0) stringResource(R.string.autotimer_more_targets, shown, more) else shown
}

@Composable
private fun daysSummary(days: List<DayFilter>): String? {
    if (days.isEmpty()) {
        return null
    }
    val weekdays = stringResource(R.string.autotimer_weekdays)
    val weekend = stringResource(R.string.autotimer_weekend)
    val locale = LocalConfiguration.current.locales[0]
    return days.joinToString(", ") { day ->
        when (day) {
            is DayFilter.On -> day.day.getDisplayName(TextStyle.SHORT, locale)
            DayFilter.Weekdays -> weekdays
            DayFilter.Weekend -> weekend
        }
    }
}

private const val SEPARATOR = " · "
private const val SHOWN_TARGETS = 2
