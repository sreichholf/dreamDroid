package net.reichholf.dreamdroid.ui.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

data class DeviceInfoRow(
    val title: String,
    val subtitle: String,
)

class DeviceInfoUiState {
    var guiVersion by mutableStateOf("")
        private set
    var imageVersion by mutableStateOf("")
        private set
    var interfaceVersion by mutableStateOf("")
        private set
    var frontProcessorVersion by mutableStateOf("")
        private set
    var deviceName by mutableStateOf("")
        private set
    var frontends by mutableStateOf<List<DeviceInfoRow>>(emptyList())
        private set
    var nics by mutableStateOf<List<DeviceInfoRow>>(emptyList())
        private set
    var hdds by mutableStateOf<List<DeviceInfoRow>>(emptyList())
        private set
    var ready by mutableStateOf(false)
        private set

    fun apply(info: DeviceInfo?, hddCapacityFormat: (capacity: String, free: String) -> String) {
        if (info == null || info.isEmpty()) {
            ready = true
            return
        }
        guiVersion = info.guiVersion
        imageVersion = info.imageVersion
        interfaceVersion = info.interfaceVersion
        frontProcessorVersion = info.frontProcessorVersion
        deviceName = info.deviceName
        frontends = info.frontends.map { DeviceInfoRow(it.name, it.model) }
        nics = info.nics.map { DeviceInfoRow(it.name, it.ip) }
        hdds = info.hdds.map { DeviceInfoRow(it.model, hddCapacityFormat(it.capacity, it.free)) }
        ready = true
    }

    fun beginLoading() {
        ready = false
        guiVersion = ""
        imageVersion = ""
        interfaceVersion = ""
        frontProcessorVersion = ""
        deviceName = ""
        frontends = emptyList()
        nics = emptyList()
        hdds = emptyList()
    }
}

@Composable
fun DeviceInfoScreen(
    state: DeviceInfoUiState,
    modifier: Modifier = Modifier,
) {
    val loading = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DeviceInfoField(
            label = stringResource(R.string.gui_version),
            value = if (state.ready) state.guiVersion else loading,
        )
        DeviceInfoField(
            label = stringResource(R.string.image_version),
            value = if (state.ready) state.imageVersion else loading,
        )
        DeviceInfoField(
            label = stringResource(R.string.interface_version),
            value = if (state.ready) state.interfaceVersion else loading,
        )
        DeviceInfoField(
            label = stringResource(R.string.front_processor_version),
            value = if (state.ready) state.frontProcessorVersion else loading,
        )
        DeviceInfoField(
            label = stringResource(R.string.device_name),
            value = if (state.ready) state.deviceName else loading,
        )
        DeviceInfoSection(
            label = stringResource(R.string.frontends),
            rows = state.frontends,
            ready = state.ready,
            loading = loading,
        )
        DeviceInfoSection(
            label = stringResource(R.string.nics),
            rows = state.nics,
            ready = state.ready,
            loading = loading,
        )
        DeviceInfoSection(
            label = stringResource(R.string.hdds),
            rows = state.hdds,
            ready = state.ready,
            loading = loading,
        )
    }
}

@Composable
private fun DeviceInfoField(
    label: String,
    value: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 2.dp),
    )
}

@Composable
private fun DeviceInfoSection(
    label: String,
    rows: List<DeviceInfoRow>,
    ready: Boolean,
    loading: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
    if (!ready) {
        Text(
            text = loading,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, top = 2.dp),
        )
        return
    }
    if (rows.isEmpty()) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, top = 2.dp),
        )
        return
    }
    rows.forEach { row ->
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
        Text(
            text = row.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
    }
}

fun ComposeView.bindDeviceInfoScreen(
    state: DeviceInfoUiState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                DeviceInfoScreen(state = state)
            }
        }
    }
}
