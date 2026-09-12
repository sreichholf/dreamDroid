package net.reichholf.dreamdroid.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Shell-facing state for the Tools bottom destination bar.
 * Mutable Snapshot fields so [bindToolsDestinationBar] recomposes when the hub updates selection.
 */
class ToolsHubState {
	var selected by mutableStateOf(ToolsDestination.SCREENSHOT)
	var onDestinationSelected: (ToolsDestination) -> Unit = {}
}

fun ComposeView.bindToolsDestinationBar(state: ToolsHubState) {
	setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
	setContent {
		DreamDroidTheme {
			ToolsDestinationBar(
				selected = state.selected,
				onDestinationSelected = { state.onDestinationSelected(it) },
			)
		}
	}
}
