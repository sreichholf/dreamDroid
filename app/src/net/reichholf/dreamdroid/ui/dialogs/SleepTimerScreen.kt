package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.stephenvinouze.materialnumberpickercore.MaterialNumberPicker
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class SleepTimerUiState(
    initialMinutes: Int,
    initialEnabled: Boolean,
    initialAction: String,
) {
    var minutes by mutableIntStateOf(initialMinutes.coerceIn(0, 999))
    var enabled by mutableStateOf(initialEnabled)
    var action by mutableStateOf(
        if (initialAction == SleepTimer.ACTION_SHUTDOWN) SleepTimer.ACTION_SHUTDOWN
        else SleepTimer.ACTION_STANDBY,
    )
}

@Composable
fun SleepTimerScreen(
    state: SleepTimerUiState,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val separatorColor = MaterialTheme.colorScheme.primary.toArgb()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { ctx ->
                MaterialNumberPicker(ctx).apply {
                    minValue = 0
                    maxValue = 999
                    value = state.minutes
                    this.textColor = textColor
                    this.separatorColor = separatorColor
                    setOnValueChangedListener { _, _, newVal ->
                        state.minutes = newVal
                    }
                }
            },
            modifier = Modifier.width(80.dp),
            update = { picker ->
                if (picker.value != state.minutes) {
                    picker.value = state.minutes
                }
                if (picker.textColor != textColor) {
                    picker.textColor = textColor
                }
                if (picker.separatorColor != separatorColor) {
                    picker.separatorColor = separatorColor
                }
            },
        )
        Column(modifier = Modifier.padding(start = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = state.enabled,
                    onClick = { state.enabled = !state.enabled },
                    role = Role.Checkbox,
                ),
            ) {
                Checkbox(
                    checked = state.enabled,
                    onCheckedChange = { state.enabled = it },
                )
                Text(
                    text = stringResource(R.string.activate),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = state.action == SleepTimer.ACTION_STANDBY,
                    onClick = { state.action = SleepTimer.ACTION_STANDBY },
                    role = Role.RadioButton,
                ),
            ) {
                RadioButton(
                    selected = state.action == SleepTimer.ACTION_STANDBY,
                    onClick = { state.action = SleepTimer.ACTION_STANDBY },
                )
                Text(
                    text = stringResource(R.string.standby),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = state.action == SleepTimer.ACTION_SHUTDOWN,
                    onClick = { state.action = SleepTimer.ACTION_SHUTDOWN },
                    role = Role.RadioButton,
                ),
            ) {
                RadioButton(
                    selected = state.action == SleepTimer.ACTION_SHUTDOWN,
                    onClick = { state.action = SleepTimer.ACTION_SHUTDOWN },
                )
                Text(
                    text = stringResource(R.string.shutdown),
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
