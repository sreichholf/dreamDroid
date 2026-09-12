package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Startup / active profile check hard-failure dialog.
 * Recheck retries the profile; Profiles opens the profile list.
 */
@Composable
fun ProfileCheckFailedDialog(
	title: String,
	message: String,
	onRecheck: () -> Unit,
	onProfiles: () -> Unit,
	onDismissRequest: () -> Unit = {},
) {
	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(title) },
		text = { Text(message) },
		confirmButton = {
			TextButton(onClick = onRecheck) {
				Text(stringResource(R.string.recheck))
			}
		},
		dismissButton = {
			TextButton(onClick = onProfiles) {
				Text(stringResource(R.string.profiles))
			}
		},
	)
}

fun ComposeView.bindProfileCheckFailedDialog(
	title: String,
	message: String,
	onRecheck: () -> Unit,
	onProfiles: () -> Unit,
) {
	setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
	setContent {
		DreamDroidTheme {
			ProfileCheckFailedDialog(
				title = title,
				message = message,
				onRecheck = onRecheck,
				onProfiles = onProfiles,
			)
		}
	}
}
