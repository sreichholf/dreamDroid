package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.PreviewMatch
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListSectionHeader
import net.reichholf.dreamdroid.ui.epg.EpgDetailModalSheet
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.text.asString

/**
 * What an AutoTimer would record: its summary, then the upcoming events and the skipped ones.
 * A tap on an upcoming event opens its EPG sheet; a tap on a skipped one shows the plugin's
 * reason.
 */
@Composable
fun AutoTimerPreviewScreen(
    state: AutoTimerPreviewUiState,
    onRefresh: () -> Unit,
    onEnable: () -> Unit,
    onToggleLog: (PreviewMatch) -> Unit,
    onOpenMatch: (PreviewMatch) -> Unit,
    onDismissMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        DreamDroidPullRefresh(
            refreshing = state.refreshing,
            onRefresh = onRefresh,
            enabled = !state.pending
        ) {
            when (val content = state.content) {
                AutoTimerPreviewContent.Loading -> ListEmptyState(loading = true, message = null)

                AutoTimerPreviewContent.Disabled -> DisabledPreview(
                    autoTimer = state.autoTimer,
                    enabled = !state.pending,
                    onEnable = onEnable
                )

                is AutoTimerPreviewContent.Ready -> LazyColumn(Modifier.fillMaxSize()) {
                    state.autoTimer?.let { summary(it) }
                    matches(
                        header = R.string.autotimer_upcoming,
                        matches = content.upcoming,
                        empty = R.string.autotimer_no_matches,
                        onClick = onOpenMatch
                    )
                    if (content.skipped.isNotEmpty()) {
                        matches(
                            header = R.string.autotimer_skipped,
                            matches = content.skipped,
                            expanded = state.expanded,
                            // A skipped event without a reason opens like an upcoming one.
                            onClick = { if (it.log.isEmpty()) onOpenMatch(it) else onToggleLog(it) }
                        )
                    }
                }

                AutoTimerPreviewContent.Gone -> ListEmptyState(
                    loading = false,
                    message = stringResource(R.string.autotimer_gone)
                )

                AutoTimerPreviewContent.PluginMissing -> ListEmptyState(
                    loading = false,
                    message = stringResource(R.string.autotimer_not_installed),
                    onRetry = onRefresh
                )

                is AutoTimerPreviewContent.PluginFailed -> ListEmptyState(
                    loading = false,
                    message = stringResource(R.string.autotimer_plugin_failed, content.message),
                    onRetry = onRefresh
                )

                is AutoTimerPreviewContent.Failed -> ListEmptyState(
                    loading = false,
                    message = content.message.asString(),
                    onRetry = onRefresh
                )
            }
        }
        if (state.pending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
    state.detail?.let { AutoTimerMatchSheet(detail = it, onDismiss = onDismissMatch) }
}

/**
 * The EPG sheet of an upcoming match, without its actions. It shows what the match itself
 * says until the EPG lookup is done.
 */
@Composable
fun AutoTimerMatchSheet(detail: AutoTimerMatchDetail, onDismiss: () -> Unit) {
    val event = when (val epg = detail.epg) {
        is MatchEpg.Found -> epg.event

        MatchEpg.Loading -> detail.match.toEvent(description = "")

        MatchEpg.Missing ->
            detail.match.toEvent(description = stringResource(R.string.autotimer_no_epg))
    }
    EpgDetailModalSheet(
        content = event.toEpgDetailContentOrUnavailable(
            stringResource(R.string.minutes_short),
            stringResource(R.string.not_available)
        ),
        onDismiss = onDismiss,
        onSetTimer = {},
        onEditTimer = {},
        onImdb = {},
        onSimilar = {},
        showActions = false
    )
}

private fun PreviewMatch.toEvent(description: String) = Event(
    title = title,
    start = begin.epochSecond.toString(),
    duration = (end.epochSecond - begin.epochSecond).toString(),
    description = description,
    serviceReference = serviceRef,
    serviceName = serviceName
)

@Composable
private fun DisabledPreview(autoTimer: AutoTimer?, enabled: Boolean, onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (autoTimer != null) {
            summaryLines(autoTimer.settings).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = stringResource(R.string.autotimer_preview_disabled),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onEnable, enabled = enabled) {
            Text(stringResource(R.string.autotimer_enable))
        }
    }
}

private fun LazyListScope.summary(autoTimer: AutoTimer) {
    item(key = "summary") {
        Column(
            modifier = Modifier.padding(
                horizontal = ListRowHorizontalInset + 16.dp,
                vertical = 8.dp
            )
        ) {
            summaryLines(autoTimer.settings).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun LazyListScope.matches(
    header: Int,
    matches: List<PreviewMatch>,
    empty: Int? = null,
    expanded: Set<String> = emptySet(),
    onClick: (PreviewMatch) -> Unit
) {
    item(key = "header-$header") {
        ListSectionHeader(text = stringResource(header, matches.size))
    }
    if (matches.isEmpty() && empty != null) {
        item(key = "empty-$header") {
            Text(
                text = stringResource(empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = ListRowHorizontalInset + 16.dp)
            )
        }
    }
    items(matches, key = { "$header-${it.key}" }) { match ->
        MatchRow(
            match = match,
            showLog = match.key in expanded,
            logState = if (match.log.isEmpty() || header != R.string.autotimer_skipped) {
                null
            } else {
                stringResource(
                    if (match.key in expanded) {
                        R.string.autotimer_reason_shown
                    } else {
                        R.string.autotimer_reason_hidden
                    }
                )
            },
            onClick = { onClick(match) }
        )
    }
}

@Composable
private fun MatchRow(
    match: PreviewMatch,
    showLog: Boolean,
    logState: String?,
    onClick: () -> Unit
) {
    val described = if (logState != null) {
        Modifier.semantics { stateDescription = logState }
    } else {
        Modifier
    }
    ListRow(
        overlineContent = {
            Text("${formatBegin(match.begin)} · ${match.serviceName}")
        },
        headlineContent = {
            Text(
                text = match.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = if (showLog && match.log.isNotEmpty()) {
            { Text(match.log, style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .then(described)
    )
}

@Composable
private fun formatBegin(begin: Instant): String {
    val zoned = begin.atZone(ZoneId.systemDefault())
    val locale = LocalConfiguration.current.locales[0]
    val weekday = DateTimeFormatter.ofPattern("EEE", locale).format(zoned)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(zoned)
    return "$weekday, $date, ${zoned.format(rememberClockFormat())}"
}
