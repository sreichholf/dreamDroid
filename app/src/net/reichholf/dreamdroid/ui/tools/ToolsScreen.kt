package net.reichholf.dreamdroid.ui.tools

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DestinationBar
import net.reichholf.dreamdroid.ui.nav.DestinationBarItem

@Composable
fun ToolsDestinationBar(
	selected: ToolsDestination,
	onDestinationSelected: (ToolsDestination) -> Unit,
	modifier: Modifier = Modifier,
) {
	val items = ToolsDestination.entries.map { dest ->
		DestinationBarItem(
			labelRes = destinationLabelRes(dest),
			iconRes = resolveThemeDrawable(destinationIconAttr(dest)),
		)
	}
	DestinationBar(
		items = items,
		selectedIndex = selected.ordinal,
		onSelect = { onDestinationSelected(ToolsDestination.entries[it]) },
		modifier = modifier,
	)
}

private fun destinationLabelRes(dest: ToolsDestination): Int = when (dest) {
	ToolsDestination.SCREENSHOT -> R.string.screenshot
	ToolsDestination.DEVICE_INFO -> R.string.device_info
	ToolsDestination.SIGNAL -> R.string.signal_meter
}

@AttrRes
private fun destinationIconAttr(dest: ToolsDestination): Int = when (dest) {
	ToolsDestination.SCREENSHOT -> R.attr.ic_menu_picture
	ToolsDestination.DEVICE_INFO -> R.attr.ic_menu_device
	ToolsDestination.SIGNAL -> R.attr.ic_menu_signal
}

@Composable
private fun resolveThemeDrawable(@AttrRes attr: Int): Int {
	val context = LocalContext.current
	val typed = TypedValue()
	context.theme.resolveAttribute(attr, typed, true)
	return typed.resourceId
}
