package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onUserInteraction: (() -> Unit)? = null,
    onScrollInProgress: ((Boolean) -> Unit)? = null
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
                currentServiceRef = currentRef,
                firstItemFocusRequester = firstItemFocusRequester,
                onUserInteraction = onUserInteraction,
                onScrollInProgress = onScrollInProgress
            )
        }
    }
}

fun ComposeView.bindTvZapList(
    state: VideoOverlayUiState,
    onServiceClick: (ServiceNowNext) -> Unit,
    onUserInteraction: (() -> Unit)? = null,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // Focusable shell so nextFocusUp from overlay chrome lands here; then forward
    // into the first Live TV card (same View→Compose bridge as bindVideoOverlayScreen).
    isFocusable = true
    isFocusableInTouchMode = true
    val firstCardFocus = FocusRequester()
    setContent {
        TvZapList(
            services = state.zapServices,
            currentRef = state.zapCurrentRef,
            onServiceClick = onServiceClick,
            firstItemFocusRequester = firstCardFocus,
            onUserInteraction = onUserInteraction,
            onScrollInProgress = onScrollInProgress
        )
    }
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            post {
                try {
                    firstCardFocus.requestFocus()
                } catch (_: IllegalStateException) {
                    // Composition not ready yet.
                }
            }
        }
    }
}
