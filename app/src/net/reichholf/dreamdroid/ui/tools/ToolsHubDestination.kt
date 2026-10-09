package net.reichholf.dreamdroid.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.ui.bouqueteditor.BouquetListDestination
import net.reichholf.dreamdroid.ui.device.DeviceInfoDestination
import net.reichholf.dreamdroid.ui.nav.LocalShellChromeScrollState
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.RegisterShellDestinationBar
import net.reichholf.dreamdroid.ui.nav.ShellDestinationBarContent
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.signal.SignalDestination

/**
 * Tools hub: Screenshot / Device Info / Signal Meter / Bouquets with shell destination chrome
 * (shell bottom chrome, owned by
 * [net.reichholf.dreamdroid.ui.nav.ProvideShellDestinationBar]).
 */
@Composable
fun ToolsHubDestination(handle: PhoneNavHandle, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(ToolsDestination.SCREENSHOT) }
    val destinationBarState = remember { ToolsHubState() }
    destinationBarState.selected = selected
    val chromeScroll = LocalShellChromeScrollState.current
    destinationBarState.onDestinationSelected = {
        selected = it
        chromeScroll?.revealAll()
    }

    // Publish Snapshot state to the shell; do not draw the bar from this leaf (content load
    // must not dispose chrome).
    RegisterShellDestinationBar(ShellDestinationBarContent.Tools(destinationBarState))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selected) {
                ToolsDestination.SCREENSHOT -> ScreenshotDestination(handle = handle)
                ToolsDestination.DEVICE_INFO -> DeviceInfoDestination()
                ToolsDestination.SIGNAL -> SignalDestination(handle = handle)
                ToolsDestination.BOUQUETS -> BouquetListDestination(handle = handle)
            }
        }
    }
}
