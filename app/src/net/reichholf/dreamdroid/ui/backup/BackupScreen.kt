package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface

data class BackupProfileToggle(val id: Int, val label: String, val checked: Boolean = true)

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
        currentProfileLabel: String
    ) {
        replaceProfiles(
            profiles.map { profile ->
                val id = profile.id
                var label = String.format("%s (%s)", profile.name, profile.host)
                if (id == currentProfileId) {
                    label += " ($currentProfileLabel)"
                }
                BackupProfileToggle(id = id ?: 0, label = label, checked = true)
            }
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRowHorizontalInset)
        ) {
            Text(stringResource(R.string.backup_import))
        }
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRowHorizontalInset)
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.backup_export))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_profiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = ListRowHorizontalInset + 16.dp,
                    end = ListRowHorizontalInset + 16.dp,
                    bottom = 8.dp
                )
            )

            state.profiles.forEach { profile ->
                ListRowSurface {
                    EditSwitchRow(
                        checked = profile.checked,
                        onCheckedChange = { state.setProfileChecked(profile.id, it) },
                        label = profile.label,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.backup_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = ListRowHorizontalInset + 16.dp,
                    end = ListRowHorizontalInset + 16.dp,
                    top = 16.dp,
                    bottom = 8.dp
                )
            )

            ListRowSurface {
                EditSwitchRow(
                    checked = state.exportSettings,
                    onCheckedChange = { state.exportSettings = it },
                    label = stringResource(R.string.backup_export_settings),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
