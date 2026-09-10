package net.reichholf.dreamdroid.ui.current

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class CurrentServiceUiState {
    var serviceName by mutableStateOf("")
        private set
    var provider by mutableStateOf("")
        private set
    var serviceReference by mutableStateOf("")
        private set
    var nowTitle by mutableStateOf("")
        private set
    var nowStart by mutableStateOf("")
        private set
    var nowDuration by mutableStateOf("")
        private set
    var nowDescription by mutableStateOf("")
        private set
    var nextTitle by mutableStateOf("")
        private set
    var nextStart by mutableStateOf("")
        private set
    var nextDuration by mutableStateOf("")
        private set
    var nextDescription by mutableStateOf("")
        private set
    var ready by mutableStateOf(false)
        private set

    fun apply(current: CurrentService?) {
        if (current == null || current.isEmpty()) {
            // Request finished with nothing usable: stop Loading placeholders without inventing data.
            ready = true
            return
        }
        val service = current.service
        serviceName = service.name
        provider = service.provider
        serviceReference = service.reference
        val now = current.now
        nowTitle = now?.title.orEmpty()
        nowStart = now?.startReadable.orEmpty()
        nowDuration = now?.durationReadable.orEmpty()
        nowDescription = now?.descriptionExtended.orEmpty()
        val next = current.next
        nextTitle = next?.title.orEmpty()
        nextStart = next?.startReadable.orEmpty()
        nextDuration = next?.durationReadable.orEmpty()
        nextDescription = next?.descriptionExtended.orEmpty()
        ready = true
    }

    fun clear() {
        serviceName = ""
        provider = ""
        serviceReference = ""
        nowTitle = ""
        nowStart = ""
        nowDuration = ""
        nowDescription = ""
        nextTitle = ""
        nextStart = ""
        nextDuration = ""
        nextDescription = ""
        ready = false
    }
}

@Composable
fun CurrentServiceScreen(
    state: CurrentServiceUiState,
    onNowClick: () -> Unit,
    onNextClick: () -> Unit,
    onStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horz = dimensionResource(R.dimen.content_horz_padding)
    val vert = dimensionResource(R.dimen.content_vert_padding)
    val context = LocalContext.current
    // Read prefs each composition so Settings toggles apply when returning here.
    val piconsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))
    val loading = stringResource(R.string.loading)
    fun displayOrLoading(value: String): String =
        if (!state.ready) loading else value

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horz, vertical = vert),
    ) {
        SectionHeader(stringResource(R.string.service))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (piconsEnabled &&
                (state.serviceReference.isNotEmpty() || state.serviceName.isNotEmpty())
            ) {
                ServicePicon(
                    reference = state.serviceReference,
                    name = state.serviceName,
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(48.dp)
                        .height(36.dp),
                )
            }
            Text(
                text = displayOrLoading(state.serviceName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(3.dp),
            )
        }

        SectionHeader(stringResource(R.string.provider))
        Text(
            text = displayOrLoading(state.provider),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .padding(bottom = 6.dp),
        )

        SectionHeader(stringResource(R.string.now))
        EventBlock(
            ready = state.ready,
            title = state.nowTitle,
            start = state.nowStart,
            duration = state.nowDuration,
            description = state.nowDescription,
            onClick = onNowClick,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        SectionHeader(stringResource(R.string.next))
        EventBlock(
            ready = state.ready,
            title = state.nextTitle,
            start = state.nextStart,
            duration = state.nextDuration,
            description = state.nextDescription,
            onClick = onNextClick,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Button(
            onClick = onStream,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(stringResource(R.string.stream_current))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
    )
}

@Composable
private fun EventBlock(
    ready: Boolean,
    title: String,
    start: String,
    duration: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = stringResource(R.string.loading)
    fun displayOrLoading(value: String): String = if (!ready) loading else value
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Text(
            text = displayOrLoading(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = displayOrLoading(start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = displayOrLoading(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
        Text(
            text = displayOrLoading(description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServicePicon(
    reference: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier,
        update = { view ->
            Picon.setPiconForView(
                context,
                view,
                reference,
                name,
                Statics.TAG_PICON,
                null,
            )
        },
    )
}

fun ComposeView.bindCurrentServiceScreen(
    state: CurrentServiceUiState,
    refresh: ComposeRefreshState,
    onRefresh: () -> Unit,
    onNowClick: () -> Unit,
    onNextClick: () -> Unit,
    onStream: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            DreamDroidPullRefresh(
                refreshing = refresh.isRefreshing,
                onRefresh = onRefresh,
                enabled = refresh.enabled,
            ) {
                CurrentServiceScreen(
                    state = state,
                    onNowClick = onNowClick,
                    onNextClick = onNextClick,
                    onStream = onStream,
                )
            }
        }
    }
}
