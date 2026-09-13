package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    val colors = DatePickerDefaults.colors()
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
        colors = colors,
    ) {
        DatePicker(
            state = dateState,
            colors = colors,
            title = {
                Text(
                    text = stringResource(R.string.epg_pick_date),
                    color = colors.titleContentColor,
                )
            },
        )
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
    val colors = TimePickerDefaults.colors()
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.epg_pick_time),
                color = scheme.onSurface,
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = timeState, colors = colors)
            }
        },
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
        containerColor = colors.containerColor,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurface,
    )
}
