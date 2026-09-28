package net.reichholf.dreamdroid.ui.signal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

@Composable
fun SignalScreen(
    state: SignalUiState,
    onEnabledChange: (Boolean) -> Unit,
    onAcousticChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val signal = state.signal
    Column(
        modifier = modifier
            .fillMaxSize()
            .onlineOnlyLook(state.blocked)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.enabled,
                    onValueChange = onEnabledChange,
                    role = Role.Switch
                )
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.enable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = null
            )
        }

        SignalGauge(
            percent = signal?.snrPercent ?: 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        MetricRow(label = "SNRdb", value = displayOrDash(signal?.snrDbRaw))
        MetricRow(label = "BER", value = displayOrDash(signal?.berRaw))
        MetricRow(label = "AGC", value = displayOrDash(signal?.agcRaw))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp)
                .toggleable(
                    value = state.acousticFeedback,
                    onValueChange = onAcousticChange,
                    role = Role.Checkbox
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.acousticFeedback,
                onCheckedChange = null
            )
            Text(
                text = stringResource(R.string.accoustic_feedback),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

private fun displayOrDash(raw: String?): String = raw?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
