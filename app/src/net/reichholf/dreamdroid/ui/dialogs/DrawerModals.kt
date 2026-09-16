package net.reichholf.dreamdroid.ui.dialogs

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer

/**
 * Phase 2.1g-ii-c: Material 3 [AlertDialog] wrappers for drawer modals
 * (no DialogFragment / MaterialAlertDialogBuilder host).
 */

@Composable
fun PowerStateDialog(onDismiss: () -> Unit, onChoice: (Int) -> Unit) {
    val items = listOf(
        PowerChoiceItem(Statics.ITEM_TOGGLE_STANDBY, stringResource(R.string.standby)),
        PowerChoiceItem(Statics.ITEM_RESTART_GUI, stringResource(R.string.restart_gui)),
        PowerChoiceItem(Statics.ITEM_REBOOT, stringResource(R.string.reboot)),
        PowerChoiceItem(Statics.ITEM_SHUTDOWN, stringResource(R.string.shutdown))
    )
    BasicAlertDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.powercontrol),
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor
        )
        PowerStateScreen(
            items = items,
            onItemClick = {
                onChoice(it.id)
                onDismiss()
            },
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
fun SendMessageDialog(
    onDismiss: () -> Unit,
    onSend: (text: String, type: String, timeout: String) -> Unit
) {
    val state = remember { SendMessageUiState() }
    BasicAlertDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.send_message),
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor
        )
        SendMessageScreen(
            state = state,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(
                onClick = {
                    onSend(state.message, state.typeIndex.toString(), state.timeout)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    initialMinutes: Int,
    initialEnabled: Boolean,
    initialAction: String,
    onDismiss: () -> Unit,
    onSave: (time: String, action: String, enabled: Boolean) -> Unit
) {
    val state = remember(initialMinutes, initialEnabled, initialAction) {
        SleepTimerUiState(initialMinutes, initialEnabled, initialAction)
    }
    BasicAlertDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sleeptimer),
            style = MaterialTheme.typography.headlineSmall,
            color = AlertDialogDefaults.titleContentColor
        )
        SleepTimerScreen(
            state = state,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(
                onClick = {
                    onSave(state.minutes.toString(), state.action, state.enabled)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
fun ChangelogDialog(onDismiss: () -> Unit, markdown: String = rememberChangelogMarkdown()) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changelog)) },
        text = { ChangelogScreen(markdown = markdown) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun rememberChangelogMarkdown(): String {
    val context = LocalContext.current
    return remember { loadChangelogMarkdown(context) }
}

private const val CHANGELOG_LOG_TAG = "Changelog"

fun loadChangelogMarkdown(context: Context): String {
    val fallback = context.getString(R.string.get_content_error)
    return try {
        context.resources.openRawResource(R.raw.changelog).use { input ->
            val text = readChangelogUtf8(input)
            if (text == null) {
                Log.e(CHANGELOG_LOG_TAG, "Failed to read changelog")
                fallback
            } else {
                text
            }
        }
    } catch (e: IOException) {
        Log.e(CHANGELOG_LOG_TAG, "Failed to read changelog", e)
        fallback
    }
}

internal fun readChangelogUtf8(input: InputStream): String? = try {
    val baos = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    var length: Int
    while (input.read(buffer).also { length = it } != -1) {
        baos.write(buffer, 0, length)
    }
    baos.toString("UTF-8")
} catch (_: IOException) {
    null
}

/** Defaults when sleep-timer HTTP load fails but we still want a form. */
fun defaultSleepTimerAction(): String = SleepTimer.ACTION_STANDBY
