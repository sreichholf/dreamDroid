package net.reichholf.dreamdroid.ui.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.dimensionResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.device.DeviceInfoDestination
import net.reichholf.dreamdroid.ui.nav.RegisterShellDestinationBar
import net.reichholf.dreamdroid.ui.nav.ShellDestinationBarContent
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.signal.SignalDestination

/**
 * Tools hub: Screenshot / Device Info / Signal Meter with a shell bottom destination bar
 * (Coordinator slot owned by [net.reichholf.dreamdroid.ui.nav.ProvideShellDestinationBar]).
 */
@Composable
fun ToolsHubDestination(modifier: Modifier = Modifier) {
	var selected by rememberSaveable { mutableStateOf(ToolsDestination.SCREENSHOT) }
	val destinationBarState = remember { ToolsHubState() }
	destinationBarState.selected = selected
	destinationBarState.onDestinationSelected = { selected = it }

	// Publish Snapshot state to the NavHost-owned shell ComposeView — do not install or
	// setContent on shell_destination_nav from this leaf (content load must not dispose chrome).
	RegisterShellDestinationBar(ShellDestinationBarContent.Tools(destinationBarState))

	Scaffold(
		modifier = modifier.fillMaxSize(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		containerColor = MaterialTheme.colorScheme.background,
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding),
		) {
			Box(
				modifier = Modifier
					.weight(1f)
					.fillMaxSize(),
			) {
				when (selected) {
					ToolsDestination.SCREENSHOT -> ScreenshotDestination()
					ToolsDestination.DEVICE_INFO -> DeviceInfoDestination()
					ToolsDestination.SIGNAL -> SignalDestination()
				}
			}
			// Reserve space for the Coordinator-hosted destination bar.
			Spacer(
				Modifier.height(dimensionResource(R.dimen.shell_destination_bar_height)),
			)
		}
	}
}
