package net.reichholf.dreamdroid.ui.profilecheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

/**
 * Full-screen profile-check gate shown on [net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes.PROFILE_CHECK].
 */
sealed class ProfileCheckUi {
	data class Checking(val message: String) : ProfileCheckUi()
	data class Failed(val title: String, val message: String) : ProfileCheckUi()
}

@Composable
fun ProfileCheckScreen(
	ui: ProfileCheckUi,
	onRecheck: () -> Unit,
	onProfiles: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(horizontal = 24.dp, vertical = 32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		when (ui) {
			is ProfileCheckUi.Checking -> {
				CircularProgressIndicator(modifier = Modifier.size(48.dp))
				Spacer(modifier = Modifier.height(24.dp))
				Text(
					text = ui.message.ifBlank { stringResource(R.string.checking_connection) },
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
			}
			is ProfileCheckUi.Failed -> {
				Text(
					text = stringResource(R.string.connection_error),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					text = ui.title,
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(modifier = Modifier.height(16.dp))
				Text(
					text = ui.message,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(modifier = Modifier.height(32.dp))
				Button(
					onClick = onRecheck,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.recheck))
				}
				Spacer(modifier = Modifier.height(12.dp))
				OutlinedButton(
					onClick = onProfiles,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.profiles))
				}
			}
		}
	}
}
