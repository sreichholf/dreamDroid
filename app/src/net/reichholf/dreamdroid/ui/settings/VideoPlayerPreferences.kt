package net.reichholf.dreamdroid.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.ExternalPlayer
import net.reichholf.dreamdroid.video.Engine

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

/**
 * The integrated player's engine. With Media3 chosen, names the receiver formats this device
 * cannot decode; those streams fall back to VLC.
 */
@Composable
internal fun PlayerEnginePreferenceRow(
    settings: AppSettings,
    media3Unsupported: List<String>,
    onOpenDialog: (ListDialogSpec) -> Unit
) {
    val title = stringResource(R.string.player_engine)
    val entries = stringArrayResource(R.array.player_engine_entries)
    val values = stringArrayResource(R.array.player_engine_values)
    val label = entryLabel(entries, values, settings.playerEngine)
    ListPreferenceRow(
        title = title,
        summary = if (settings.engine == Engine.Media3 && media3Unsupported.isNotEmpty()) {
            label + "\n" + stringResource(
                R.string.player_engine_media3_missing,
                media3Unsupported.joinToString(", ")
            )
        } else {
            label
        },
        enabled = settings.integratedVideoPlayer,
        onClick = {
            onOpenDialog(
                ListDialogSpec(
                    title = title,
                    entries = entries.toList(),
                    values = values.toList(),
                    selectedValue = settings.playerEngine,
                    set = { copy(playerEngine = it) }
                )
            )
        }
    )
}

/** How much the integrated player buffers before it plays. */
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
