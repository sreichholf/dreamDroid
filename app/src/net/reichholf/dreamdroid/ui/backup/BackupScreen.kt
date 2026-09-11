package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R

data class BackupProfileToggle(
    val id: Int,
    val label: String,
    val checked: Boolean = true,
)

class BackupUiState {
    var profiles by mutableStateOf<List<BackupProfileToggle>>(emptyList())
        private set

    /** Matches legacy XML SwitchCompat default (unchecked). */
    var exportSettings by mutableStateOf(false)

    fun replaceProfiles(items: List<BackupProfileToggle>) {
        profiles = items
    }

    fun setProfilesFromBackup(
        profiles: List<Profile>,
        currentProfileId: Int,
        currentProfileLabel: String,
    ) {
        replaceProfiles(
            profiles.map { profile ->
                val id = profile.id
                var label = String.format("%s (%s)", profile.name, profile.host)
                if (id == currentProfileId) {
                    label += " ($currentProfileLabel)"
                }
                BackupProfileToggle(id = id ?: 0, label = label, checked = true)
            },
        )
    }

    fun setProfileChecked(id: Int, checked: Boolean) {
        profiles = profiles.map { item ->
            if (item.id == id) item.copy(checked = checked) else item
        }
    }
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.backup_import))
        }
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.backup_export))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_profiles),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )

            state.profiles.forEach { profile ->
                BackupSwitchRow(
                    label = profile.label,
                    checked = profile.checked,
                    onCheckedChange = { state.setProfileChecked(profile.id ?: 0, it) },
                )
            }

            Text(
                text = stringResource(R.string.backup_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            BackupSwitchRow(
                label = stringResource(R.string.backup_export_settings),
                checked = state.exportSettings,
                onCheckedChange = { state.exportSettings = it },
            )
        }
    }
}

@Composable
private fun BackupSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
