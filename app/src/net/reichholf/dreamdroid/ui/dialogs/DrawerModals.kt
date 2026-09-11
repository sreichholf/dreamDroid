package net.reichholf.dreamdroid.ui.dialogs

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Phase 2.1g-ii-c: Material 3 [AlertDialog] wrappers for drawer modals
 * (no DialogFragment / MaterialAlertDialogBuilder host).
 */

@Composable
fun PowerStateDialog(
    onDismiss: () -> Unit,
    onChoice: (Int) -> Unit,
) {
    val items = listOf(
        PowerChoiceItem(Statics.ITEM_TOGGLE_STANDBY, stringResource(R.string.standby)),
        PowerChoiceItem(Statics.ITEM_RESTART_GUI, stringResource(R.string.restart_gui)),
        PowerChoiceItem(Statics.ITEM_REBOOT, stringResource(R.string.reboot)),
        PowerChoiceItem(Statics.ITEM_SHUTDOWN, stringResource(R.string.shutdown)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.powercontrol)) },
        text = {
            PowerStateScreen(
                items = items,
                onItemClick = {
                    onChoice(it.id)
                    onDismiss()
                },
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SendMessageDialog(
    onDismiss: () -> Unit,
    onSend: (text: String, type: String, timeout: String) -> Unit,
) {
    val state = remember { SendMessageUiState() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.send_message)) },
        text = { SendMessageScreen(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSend(state.message, state.typeIndex.toString(), state.timeout)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SleepTimerDialog(
    initialMinutes: Int,
    initialEnabled: Boolean,
    initialAction: String,
    onDismiss: () -> Unit,
    onSave: (time: String, action: String, enabled: Boolean) -> Unit,
) {
    val state = remember(initialMinutes, initialEnabled, initialAction) {
        SleepTimerUiState(initialMinutes, initialEnabled, initialAction)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleeptimer)) },
        text = { SleepTimerScreen(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(state.minutes.toString(), state.action, state.enabled)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit,
    markdown: String = rememberChangelogMarkdown(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changelog)) },
        text = { ChangelogScreen(markdown = markdown) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
fun rememberChangelogMarkdown(): String {
    val context = LocalContext.current
    return remember { loadChangelogMarkdown(context) }
}

fun loadChangelogMarkdown(context: Context): String {
    var text = ""
    val input = context.resources.openRawResource(R.raw.changelog)
    try {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (input.read(buffer).also { length = it } != -1) {
            baos.write(buffer, 0, length)
        }
        text = baos.toString("UTF-8")
    } catch (e: IOException) {
        e.printStackTrace()
    }
    IOUtils.closeQuietly(input)
    return text
}

/** Defaults when sleep-timer HTTP load fails but we still want a form. */
fun defaultSleepTimerAction(): String = SleepTimer.ACTION_STANDBY
