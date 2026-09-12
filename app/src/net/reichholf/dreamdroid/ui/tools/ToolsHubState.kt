package net.reichholf.dreamdroid.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Snapshot state for the Tools shell destination bar.
 * Published to [net.reichholf.dreamdroid.ui.nav.ProvideShellDestinationBar] via
 * [net.reichholf.dreamdroid.ui.nav.RegisterShellDestinationBar]; the shell ComposeView
 * reads these fields directly (no hub `@Composable` capture).
 */
class ToolsHubState {
	var selected by mutableStateOf(ToolsDestination.SCREENSHOT)
	var onDestinationSelected: (ToolsDestination) -> Unit = {}
}
