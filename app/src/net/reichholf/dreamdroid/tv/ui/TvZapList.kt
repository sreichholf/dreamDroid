package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState

/**
 * TV overlay zap list: the same Live TV image+info cards as the hub service row
 * (picon, now/next, 1.05 focus scale, DreamDroid palette).
 *
 * [DreamDroidTvTheme.fillBackground] stays false so the parent
 * [net.reichholf.dreamdroid.fragment.VideoOverlayFragment.overlayAlpha] fade is
 * what keeps video visible — cards use phone surface tokens, not Leanback gray.
 */
@Composable
fun TvZapList(
    services: List<ServiceNowNext>,
    currentRef: String?,
    onServiceClick: (ServiceNowNext) -> Unit,
    modifier: Modifier = Modifier
) {
    DreamDroidTvTheme(fillBackground = false) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .testTag("overlay_zap_list")
        ) {
            HubServiceRow(
                bouquetRef = "",
                services = services,
                onServiceClick = { service, _ -> onServiceClick(service) },
                currentServiceRef = currentRef
            )
        }
    }
}

fun ComposeView.bindTvZapList(
    state: VideoOverlayUiState,
    onServiceClick: (ServiceNowNext) -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    isFocusable = true
    isFocusableInTouchMode = true
    setContent {
        TvZapList(
            services = state.zapServices,
            currentRef = state.zapCurrentRef,
            onServiceClick = onServiceClick
        )
    }
}
