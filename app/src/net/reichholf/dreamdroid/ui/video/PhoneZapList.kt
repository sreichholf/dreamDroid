package net.reichholf.dreamdroid.ui.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.services.ServiceRow
import net.reichholf.dreamdroid.ui.services.ServiceRowKind
import net.reichholf.dreamdroid.ui.services.serviceListItemsFromNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

const val OVERLAY_PHONE_ZAP_LIST_TAG = "overlay_phone_zap_list"

/**
 * Phone overlay channel list. Same rows as the hub service list, scrolling over
 * the video instead of a RecyclerView.
 */
@Composable
fun PhoneZapList(
    services: List<ServiceNowNext>,
    currentRef: String?,
    onServiceClick: (ServiceNowNext) -> Unit,
    modifier: Modifier = Modifier,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    val items = serviceListItemsFromNowNext(services)
    val listState = rememberLazyListState()
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling) {
        onScrollInProgress?.invoke(scrolling)
    }
    val index = items.indexOfFirst { item ->
        item.reference == currentRef && item.kind == ServiceRowKind.CHANNEL
    }
    LaunchedEffect(currentRef, items.size) {
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(OVERLAY_PHONE_ZAP_LIST_TAG),
        state = listState
    ) {
        items(items, key = { "${it.index}:${it.reference}" }) { item ->
            ServiceRow(
                item = item,
                onClick = { _, _ ->
                    val row = services.getOrNull(item.index) ?: return@ServiceRow
                    if (item.kind == ServiceRowKind.CHANNEL) {
                        onServiceClick(row)
                    }
                },
                onLongClick = { _, _ -> }
            )
        }
    }
}

fun ComposeView.bindPhoneZapList(
    state: VideoOverlayUiState,
    onServiceClick: (ServiceNowNext) -> Unit,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneZapList(
                services = state.zapServices,
                currentRef = state.zapCurrentRef,
                onServiceClick = onServiceClick,
                onScrollInProgress = onScrollInProgress
            )
        }
    }
}
