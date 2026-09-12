package net.reichholf.dreamdroid.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shell-facing state for the Tools bottom destination bar.
 * Mutable Snapshot fields so [net.reichholf.dreamdroid.ui.nav.InstallShellDestinationBar]
 * recomposes when the hub updates selection.
 */
class ToolsHubState {
	var selected by mutableStateOf(ToolsDestination.SCREENSHOT)
	var onDestinationSelected: (ToolsDestination) -> Unit = {}
}
