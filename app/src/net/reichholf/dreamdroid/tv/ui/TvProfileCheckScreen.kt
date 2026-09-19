package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/**
 * TV ProfileCheck gate. Checking / Failed copy matches the phone gate; actions
 * are focusable `androidx.tv` Surfaces (not phone [androidx.compose.material3.Button]).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfileCheckScreen(
    gate: TvSessionGate,
    onRecheck: () -> Unit,
    onProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_profile_check")
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (gate) {
            is TvSessionGate.Checking -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = gate.message.ifBlank {
                        stringResource(R.string.checking_connection)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is TvSessionGate.Failed -> {
                Text(
                    text = stringResource(R.string.connection_error),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = gate.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = gate.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                TvProfileCheckAction(
                    label = stringResource(R.string.recheck),
                    onClick = onRecheck,
                    testTag = "tv_profile_check_recheck"
                )
                Spacer(modifier = Modifier.height(12.dp))
                TvProfileCheckAction(
                    label = stringResource(R.string.profiles),
                    onClick = onProfiles,
                    testTag = "tv_profile_check_profiles"
                )
            }

            TvSessionGate.None -> Unit
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvProfileCheckAction(label: String, onClick: () -> Unit, testTag: String) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(280.dp)
            .height(56.dp)
            .testTag(testTag),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
