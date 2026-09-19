package net.reichholf.dreamdroid.ui.tools

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.DestinationBar
import net.reichholf.dreamdroid.ui.nav.DestinationBarItem
import net.reichholf.dreamdroid.ui.nav.DestinationRail
import net.reichholf.dreamdroid.ui.theme.isDreamDroidDark

@Composable
fun ToolsDestinationBar(
    selected: ToolsDestination,
    onDestinationSelected: (ToolsDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    DestinationBar(
        items = toolsDestinationItems(),
        selectedIndex = selected.ordinal,
        onSelect = { onDestinationSelected(ToolsDestination.entries[it]) },
        modifier = modifier
    )
}

@Composable
fun ToolsDestinationRail(
    selected: ToolsDestination,
    onDestinationSelected: (ToolsDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    DestinationRail(
        items = toolsDestinationItems(),
        selectedIndex = selected.ordinal,
        onSelect = { onDestinationSelected(ToolsDestination.entries[it]) },
        modifier = modifier
    )
}

@Composable
private fun toolsDestinationItems(): List<DestinationBarItem> =
    ToolsDestination.entries.map { dest ->
        DestinationBarItem(
            labelRes = destinationLabelRes(dest),
            iconRes = resolveThemeDrawable(destinationIconAttr(dest))
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
    if (context.theme.resolveAttribute(attr, typed, true) && typed.resourceId != 0) {
        return typed.resourceId
    }
    val themeRes = if (isDreamDroidDark(context)) {
        R.style.Theme_DreamDroid_Night
    } else {
        R.style.Theme_DreamDroid
    }
    ContextThemeWrapper(context, themeRes).theme.resolveAttribute(attr, typed, true)
    return typed.resourceId
}
