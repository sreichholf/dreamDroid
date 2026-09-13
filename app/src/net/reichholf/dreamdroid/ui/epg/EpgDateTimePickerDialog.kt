package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import java.util.Calendar
import java.util.TimeZone

const val EPG_DATE_TIME_PICKER_TAG = "epg_date_time_picker"

/**
 * Combined bouquet-EPG instant picker: calendar + compact time fields, one confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgDateTimePickerDialog(
    initialTimeSec: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (timeSec: Int) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = EpgInstant.utcMidnightMillis(initialTimeSec, timeZone),
    )
    val initial = Calendar.getInstance(timeZone).apply {
        timeInMillis = initialTimeSec * 1000L
    }
    val timeState = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = is24Hour,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val dateMillis = dateState.selectedDateMillis
                        ?: EpgInstant.utcMidnightMillis(initialTimeSec, timeZone)
                    onConfirm(
                        EpgInstant.combine(
                            utcDateMillis = dateMillis,
                            hour = timeState.hour,
                            minute = timeState.minute,
                            timeZone = timeZone,
                        ),
                    )
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
        modifier = modifier.testTag(EPG_DATE_TIME_PICKER_TAG),
    ) {
        DatePicker(
            state = dateState,
            title = { Text(stringResource(R.string.epg_pick_date_time)) },
            headline = null,
            showModeToggle = true,
        )
        TimeInput(
            state = timeState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
        )
    }
}
