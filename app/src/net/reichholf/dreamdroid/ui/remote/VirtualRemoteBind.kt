package net.reichholf.dreamdroid.ui.remote

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

fun ComposeView.bindVirtualRemoteScreen(
    layout: VirtualRemoteLayout,
    playButtonAsPlayPause: Boolean,
    onKey: (keyCode: Int, longClick: Boolean) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            VirtualRemoteScreen(
                layout = layout,
                playButtonAsPlayPause = playButtonAsPlayPause,
                onKey = onKey,
            )
        }
    }
}
