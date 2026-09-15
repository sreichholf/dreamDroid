package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val minutesLabel = stringResource(R.string.minutes_short)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { state.adjustMinutes(1) },
                    modifier = Modifier.testTag(SLEEP_TIMER_MINUTES_INC_TAG)
                ) {
                    Text(
                        text = "+",
                        color = onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                OutlinedTextField(
                    value = state.minutes.toString(),
                    onValueChange = { state.minutes = parseSleepTimerMinutes(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SLEEP_TIMER_MINUTES_TAG),
                    label = {
                        Text(text = minutesLabel, color = onSurface)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = onSurface,
                        textAlign = TextAlign.Center
                    )
                )
                IconButton(
                    onClick = { state.adjustMinutes(-1) },
                    modifier = Modifier.testTag(SLEEP_TIMER_MINUTES_DEC_TAG)
                ) {
                    Text(
                        text = "-",
                        color = onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(
                        selected = state.enabled,
                        onClick = { state.enabled = !state.enabled },
                        role = Role.Checkbox
                    )
                ) {
                    Checkbox(
                        checked = state.enabled,
                        onCheckedChange = { state.enabled = it }
                    )
                    Text(
                        text = stringResource(R.string.activate),
                        color = onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(
                        selected = state.action == SleepTimer.ACTION_STANDBY,
                        onClick = { state.action = SleepTimer.ACTION_STANDBY },
                        role = Role.RadioButton
                    )
                ) {
                    RadioButton(
                        selected = state.action == SleepTimer.ACTION_STANDBY,
                        onClick = { state.action = SleepTimer.ACTION_STANDBY }
                    )
                    Text(
                        text = stringResource(R.string.standby),
                        color = onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(
                        selected = state.action == SleepTimer.ACTION_SHUTDOWN,
                        onClick = { state.action = SleepTimer.ACTION_SHUTDOWN },
                        role = Role.RadioButton
                    )
                ) {
                    RadioButton(
                        selected = state.action == SleepTimer.ACTION_SHUTDOWN,
                        onClick = { state.action = SleepTimer.ACTION_SHUTDOWN }
                    )
                    Text(
                        text = stringResource(R.string.shutdown),
                        color = onSurface
                    )
                }
            }
        }
        Slider(
            value = state.minutes.toFloat(),
            onValueChange = { state.minutes = it.toInt().coerceIn(0, 999) },
            valueRange = 0f..999f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = minutesLabel }
        )
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
