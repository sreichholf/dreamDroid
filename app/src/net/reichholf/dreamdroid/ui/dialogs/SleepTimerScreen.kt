package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.compose.EditForm
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

internal const val SLEEP_TIMER_MINUTES_TAG = "sleep_timer_minutes"
internal const val SLEEP_TIMER_MINUTES_DEC_TAG = "sleep_timer_minutes_dec"
internal const val SLEEP_TIMER_MINUTES_INC_TAG = "sleep_timer_minutes_inc"

class SleepTimerUiState(initialMinutes: Int, initialEnabled: Boolean, initialAction: String) {
    var minutes by mutableIntStateOf(initialMinutes.coerceIn(0, 999))
    var enabled by mutableStateOf(initialEnabled)
    var action by mutableStateOf(
        if (initialAction == SleepTimer.ACTION_SHUTDOWN) {
            SleepTimer.ACTION_SHUTDOWN
        } else {
            SleepTimer.ACTION_STANDBY
        }
    )
}

@Composable
fun SleepTimerScreen(state: SleepTimerUiState, modifier: Modifier = Modifier) {
    val minutesLabel = stringResource(R.string.minutes_short)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EditForm.FieldSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { state.adjustMinutes(-1) },
                modifier = Modifier.testTag(SLEEP_TIMER_MINUTES_DEC_TAG)
            ) {
                Text("-")
            }
            EditOutlinedTextField(
                value = state.minutes.toString(),
                onValueChange = { state.minutes = parseSleepTimerMinutes(it) },
                label = minutesLabel,
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag(SLEEP_TIMER_MINUTES_TAG)
            )
            FilledTonalIconButton(
                onClick = { state.adjustMinutes(1) },
                modifier = Modifier.testTag(SLEEP_TIMER_MINUTES_INC_TAG)
            ) {
                Text("+")
            }
        }
        Slider(
            value = state.minutes.toFloat(),
            onValueChange = { state.minutes = it.toInt().coerceIn(0, 999) },
            valueRange = 0f..999f,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = minutesLabel }
        )
        EditSwitchRow(
            checked = state.enabled,
            onCheckedChange = { state.enabled = it },
            label = stringResource(R.string.activate)
        )
        SleepTimerActionRow(state = state)
    }
}

@Composable
private fun SleepTimerActionRow(state: SleepTimerUiState) {
    val actions = listOf(
        SleepTimer.ACTION_STANDBY to stringResource(R.string.standby),
        SleepTimer.ACTION_SHUTDOWN to stringResource(R.string.shutdown)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        actions.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = state.action == value,
                onClick = { state.action = value },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = actions.size
                )
            ) {
                Text(label)
            }
        }
    }
}

fun ComposeView.bindSleepTimerScreen(state: SleepTimerUiState) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        DreamDroidTheme {
            SleepTimerScreen(state = state)
        }
    }
}

private fun SleepTimerUiState.adjustMinutes(delta: Int) {
    minutes = (minutes + delta).coerceIn(0, 999)
}

private fun parseSleepTimerMinutes(raw: String): Int {
    val digits = raw.filter { it.isDigit() }.take(3)
    return digits.toIntOrNull()?.coerceIn(0, 999) ?: 0
}
