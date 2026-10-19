package net.reichholf.dreamdroid.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.ExternalPlayer

/** The app that plays streams while the integrated player is off. */
@Composable
internal fun ExternalPlayerPreferenceRow(
    settings: AppSettings,
    externalPlayers: List<ExternalPlayer>,
    onOpenDialog: (ListDialogSpec) -> Unit
) {
    val title = stringResource(R.string.external_player)
    val ask = stringResource(R.string.external_player_ask)
    val chosen = externalPlayers.firstOrNull { it.packageName == settings.externalPlayer }
        ?.label
        ?: settings.externalPlayer.ifEmpty { ask }
    ListPreferenceRow(
        title = title,
        summary = stringResource(R.string.external_player_long, chosen),
        enabled = !settings.integratedVideoPlayer,
        onClick = {
            onOpenDialog(
                ListDialogSpec(
                    title = title,
                    entries = listOf(ask) + externalPlayers.map { it.label },
                    values = listOf("") + externalPlayers.map { it.packageName },
                    selectedValue = settings.externalPlayer,
                    set = { copy(externalPlayer = it) }
                )
            )
        }
    )
}

/** libVLC's network caching for the integrated player. */
@Composable
internal fun StreamBufferPreferenceRow(
    settings: AppSettings,
    onOpenDialog: (ListDialogSpec) -> Unit
) {
    val title = stringResource(R.string.stream_buffer)
    val entries = stringArrayResource(R.array.stream_buffer_entries)
    val values = stringArrayResource(R.array.stream_buffer_values)
    ListPreferenceRow(
        title = title,
        summary = stringResource(
            R.string.stream_buffer_long,
            entryLabel(entries, values, settings.streamBufferMs)
        ),
        enabled = settings.integratedVideoPlayer,
        onClick = {
            onOpenDialog(
                ListDialogSpec(
                    title = title,
                    entries = entries.toList(),
                    values = values.toList(),
                    selectedValue = settings.streamBufferMs,
                    set = { copy(streamBufferMs = it) }
                )
            )
        }
    )
}
