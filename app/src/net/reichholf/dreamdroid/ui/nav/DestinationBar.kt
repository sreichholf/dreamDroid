package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass

/** One entry in the shell [DestinationBar] (TV & Movies, Tools, …). */
data class DestinationBarItem(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int)

/**
 * Shared Material 3 bottom destination bar for the hubs (TV & Movies, Tools), on phones and
 * tablets. [PhoneShell] draws it from the state hubs publish with [RegisterShellDestinationBar].
 * From medium width on, items put the icon beside the label and the bar centers them, as
 * Material 3's flexible navigation bar does for medium windows.
 */
@Composable
fun DestinationBar(
    items: List<DestinationBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val wide = isWideWindow()
    // The shell already pads by the system bars (#263/#264).
    ShortNavigationBar(
        // Its Surface hands minimum constraints on to the item layout, which then stretches
        // every item to the minimum width and pushes all but the first off the bar. Measured
        // without them, the bar still takes the full width it is offered.
        modifier = modifier.wrapContentSize(),
        windowInsets = WindowInsets(0, 0, 0, 0),
        arrangement = if (wide) {
            ShortNavigationBarArrangement.Centered
        } else {
            ShortNavigationBarArrangement.EqualWeight
        }
    ) {
        items.forEachIndexed { index, item ->
            val label = stringResource(item.labelRes)
            ShortNavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null
                    )
                },
                label = { Text(label) },
                iconPosition = if (wide) {
                    NavigationItemIconPosition.Start
                } else {
                    NavigationItemIconPosition.Top
                }
            )
        }
    }
}

/** Width size class Medium or larger: tablets, foldables open, phones in landscape. */
@Composable
internal fun isWideWindow(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
