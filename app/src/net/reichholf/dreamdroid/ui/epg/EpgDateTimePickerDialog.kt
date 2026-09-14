package net.reichholf.dreamdroid.ui.epg

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import java.util.Calendar
import java.util.TimeZone

/**
 * Stock Material 3 date picker for bouquet EPG. Keeps the current local time of
 * [initialTimeSec] when the caller applies the selected UTC midnight millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgDatePickerDialog(
    initialTimeSec: Int,
    onDismiss: () -> Unit,
    onConfirm: (utcDateMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = EpgInstant.utcMidnightMillis(initialTimeSec, timeZone),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val dateMillis = dateState.selectedDateMillis
                        ?: EpgInstant.utcMidnightMillis(initialTimeSec, timeZone)
                    onConfirm(dateMillis)
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier,
    ) {
        DatePicker(state = dateState)
    }
}

/**
 * Stock Material 3 time picker for bouquet EPG. Keeps the current local date of
 * [initialTimeSec] when the caller applies the selected hour and minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgTimePickerDialog(
    initialTimeSec: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    val initial = Calendar.getInstance(timeZone).apply {
        timeInMillis = initialTimeSec * 1000L
    }
    val timeState = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timeState.hour, timeState.minute) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
