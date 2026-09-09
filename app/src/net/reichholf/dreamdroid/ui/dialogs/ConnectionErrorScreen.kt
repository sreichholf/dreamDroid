package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

@Composable
fun ConnectionErrorScreen(
	message: String,
	onPositive: () -> Unit,
	onEditProfile: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 8.dp, vertical = 4.dp),
	) {
		Text(
			text = message,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp),
		)
		Button(
			onClick = onPositive,
			modifier = Modifier.align(Alignment.End),
		) {
			Text(stringResource(R.string.ok))
		}
		TextButton(
			onClick = onEditProfile,
			modifier = Modifier.align(Alignment.End),
		) {
			Text(stringResource(R.string.edit_profile))
		}
	}
}

fun ComposeView.bindConnectionErrorScreen(
	message: String,
	onPositive: () -> Unit,
	onEditProfile: () -> Unit,
) {
	setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
	setContent {
		DreamDroidTheme {
			ConnectionErrorScreen(
				message = message,
				onPositive = onPositive,
				onEditProfile = onEditProfile,
			)
		}
	}
}
