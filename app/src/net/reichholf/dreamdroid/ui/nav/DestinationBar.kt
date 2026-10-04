package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

const val DESTINATION_RAIL_TAG = "destination_rail"

/**
 * One entry in a phone shell bottom [DestinationBar] or tablet [DestinationRail]
 * (TV & Movies, Tools, …).
 */
data class DestinationBarItem(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int)

/**
 * Shared Material 3 bottom destination bar for phone hubs (TV & Movies, Tools).
 * [PhoneShell] draws it from the state hubs publish with [RegisterShellDestinationBar].
 */
@Composable
fun DestinationBar(
    items: List<DestinationBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // The shell already pads by the system bars (#263/#264).
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        items.forEachIndexed { index, item ->
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null
                    )
                },
                label = { Text(label) }
            )
        }
    }
}

/**
 * Material 3 start-side rail for the tablet shell. Same items as [DestinationBar].
 * The shell already pads by the system bars (#263/#264).
 */
@Composable
fun DestinationRail(
    items: List<DestinationBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.testTag(DESTINATION_RAIL_TAG),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        items.forEachIndexed { index, item ->
            val label = stringResource(item.labelRes)
            NavigationRailItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null
                    )
                },
                label = { Text(label) }
            )
        }
    }
}
