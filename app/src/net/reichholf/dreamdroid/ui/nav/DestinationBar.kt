package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R

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

/**
 * Installs destination-bar composition on the activity [R.id.shell_destination_nav]
 * ComposeView and clears it when leaving this composition.
 *
 * [bind] must call a `ComposeView.bind…(state)` helper that owns a self-contained
 * `setContent { }` reading Snapshot state. Do **not** capture a `@Composable` lambda
 * from the NavHost/`detail_view` composition into the shell ComposeView — that
 * cross-ComposeView bridge goes blank after hub content loads (Tools / TV & Movies).
 */
@Composable
fun InstallShellDestinationBar(bind: (ComposeView) -> Unit) {
	val context = LocalContext.current
	DisposableEffect(context) {
		val activity = context.findActivity()
			?: return@DisposableEffect onDispose { }
		val shellNav = activity.findViewById<ComposeView?>(R.id.shell_destination_nav)
			?: return@DisposableEffect onDispose { }
		shellNav.visibility = View.VISIBLE
		bind(shellNav)
		onDispose {
			shellNav.visibility = View.GONE
			shellNav.disposeComposition()
		}
	}
}

private fun Context.findActivity(): Activity? {
	var ctx: Context? = this
	while (ctx is ContextWrapper) {
		if (ctx is Activity) return ctx
		ctx = ctx.baseContext
	}
	return ctx as? Activity
}
