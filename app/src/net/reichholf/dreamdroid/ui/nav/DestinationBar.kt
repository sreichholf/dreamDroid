package net.reichholf.dreamdroid.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/**
 * One entry in a phone shell bottom [DestinationBar] (TV & Movies, Tools, …).
 */
data class DestinationBarItem(
	@StringRes val labelRes: Int,
	@DrawableRes val iconRes: Int,
)

/**
 * Shared Material 3 bottom destination bar for phone hubs that host chrome on the
 * activity Coordinator slot ([R.id.shell_destination_nav]).
 *
 * Installed by [ProvideShellDestinationBar] / [RegisterShellDestinationBar] — not by
 * capturing NavHost `@Composable` lambdas into the sibling activity ComposeView.
 */
@Composable
fun DestinationBar(
	items: List<DestinationBarItem>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	// Hosted on MainActivity Coordinator which already fits system windows (#263/#264).
	NavigationBar(
		modifier = modifier.fillMaxWidth(),
		windowInsets = WindowInsets(0, 0, 0, 0),
	) {
		items.forEachIndexed { index, item ->
			val label = stringResource(item.labelRes)
			NavigationBarItem(
				selected = index == selectedIndex,
				onClick = { onSelect(index) },
				icon = {
					Icon(
						painter = painterResource(item.iconRes),
						contentDescription = label,
					)
				},
				label = { Text(label) },
			)
		}
	}
}
