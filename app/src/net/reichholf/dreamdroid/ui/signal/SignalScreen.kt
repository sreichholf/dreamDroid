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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

class SignalUiState {
    var enabled by mutableStateOf(true)
    var acousticFeedback by mutableStateOf(false)
    var snrPercent by mutableIntStateOf(0)
        private set
    var snrDbRaw by mutableStateOf("-")
        private set
    var berRaw by mutableStateOf("-")
        private set
    var agcRaw by mutableStateOf("-")
        private set
    var snrDb by mutableDoubleStateOf(Signal.MIN_SNR_DB)
        private set

    fun apply(signal: Signal, minSnrDb: Double) {
        snrPercent = signal.snrPercent
        snrDbRaw = displayOrDash(signal.snrDbRaw)
        berRaw = displayOrDash(signal.berRaw)
        agcRaw = displayOrDash(signal.agcRaw)
        var db = signal.snrDb
        if (db < minSnrDb) {
            db = minSnrDb
        }
        snrDb = db
    }

    fun clearMeter() {
        snrPercent = 0
        snrDbRaw = "-"
        berRaw = "-"
        agcRaw = "-"
        snrDb = Signal.MIN_SNR_DB
    }

    private fun displayOrDash(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return "-"
        }
        return raw.trim()
    }
}

@Composable
fun SignalScreen(
    state: SignalUiState,
    onEnabledChange: (Boolean) -> Unit,
    onAcousticChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    meterBlocked: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .onlineOnlyLook(meterBlocked)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.enabled,
                    onValueChange = {
                        if (meterBlocked) {
                            onEnabledChange(it)
                            return@toggleable
                        }
                        state.enabled = it
                        onEnabledChange(it)
                    },
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
            percent = state.snrPercent,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        MetricRow(label = "SNRdb", value = state.snrDbRaw)
        MetricRow(label = "BER", value = state.berRaw)
        MetricRow(label = "AGC", value = state.agcRaw)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp)
                .toggleable(
                    value = state.acousticFeedback,
                    onValueChange = {
                        state.acousticFeedback = it
                        onAcousticChange(it)
                    },
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
