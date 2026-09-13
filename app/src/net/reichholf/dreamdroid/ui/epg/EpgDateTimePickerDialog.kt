package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.reichholf.dreamdroid.R
import java.util.Calendar
import java.util.TimeZone

const val EPG_DATE_TIME_PICKER_TAG = "epg_date_time_picker"

private const val PAGE_DATE = 0
private const val PAGE_TIME = 1

/**
 * Combined bouquet-EPG instant picker. Date and time are separate pages in one
 * dialog so each Material picker keeps its intended size; one OK applies both.
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
    var page by rememberSaveable { mutableIntStateOf(PAGE_DATE) }

    fun confirm() {
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
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag(EPG_DATE_TIME_PICKER_TAG),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.epg_pick_date_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = 24.dp,
                        end = 12.dp,
                        top = 16.dp,
                        bottom = 8.dp,
                    ),
                )
                TabRow(selectedTabIndex = page) {
                    Tab(
                        selected = page == PAGE_DATE,
                        onClick = { page = PAGE_DATE },
                        text = { Text(stringResource(R.string.epg_pick_date)) },
                    )
                    Tab(
                        selected = page == PAGE_TIME,
                        onClick = { page = PAGE_TIME },
                        text = { Text(stringResource(R.string.epg_pick_time)) },
                    )
                }
                if (page == PAGE_DATE) {
                    DatePicker(
                        state = dateState,
                        title = null,
                        headline = null,
                        showModeToggle = true,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TimePicker(state = timeState)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = { confirm() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
