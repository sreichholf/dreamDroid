package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings

/**
 * TV settings subset matching [R.xml.preferences] on television
 * (`xml-television/preferences.xml`). Same PreferenceManager keys as phone.
 */
@Composable
fun TvSettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    showDeveloperCategory: Boolean = false,
    syncPiconsPathDraft: TextFieldState? = null,
    onEditSyncPiconsPath: () -> Unit = {},
    onConfirmSyncPiconsPath: () -> Unit = {},
    onDismissSyncPiconsPath: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var listDialog by remember { mutableStateOf<ListDialogSpec?>(null) }

    val hwEntries = stringArrayResource(R.array.hw_accel_entries)
    val hwValues = stringArrayResource(R.array.hw_accel_values)
    val hwAccelDialogTitle = stringResource(R.string.video_use_hw_accel)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            PreferenceCategoryHeader(stringResource(R.string.video_player))
            SwitchPreferenceRow(
                title = stringResource(R.string.integrated_video_player),
                summary = stringResource(R.string.integrated_video_player_long),
                checked = settings.integratedVideoPlayer,
                onCheckedChange = { checked ->
                    onChange { it.copy(integratedVideoPlayer = checked) }
                }
            )
            ListPreferenceRow(
                title = stringResource(R.string.use_hw_accel),
                summary = stringResource(
                    R.string.use_hw_accel_long,
                    entryLabel(hwEntries, hwValues, settings.videoHardwareAcceleration)
                ),
                enabled = settings.integratedVideoPlayer,
                onClick = {
                    listDialog = ListDialogSpec(
                        title = hwAccelDialogTitle,
                        entries = hwEntries.toList(),
                        values = hwValues.toList(),
                        selectedValue = settings.videoHardwareAcceleration,
                        set = { copy(videoHardwareAcceleration = it) }
                    )
                }
            )

            PreferenceCategoryHeader(stringResource(R.string.picons))
            SwitchPreferenceRow(
                title = stringResource(R.string.use_name_as_picon_filename),
                summary = stringResource(R.string.use_name_as_picon_filename_long),
                checked = settings.useNameAsPiconFilename,
                onCheckedChange = { checked ->
                    onChange { it.copy(useNameAsPiconFilename = checked) }
                }
            )
            ActionPreferenceRow(
                title = stringResource(R.string.sync_picons_path),
                summary = settings.syncPiconsPath.ifEmpty {
                    stringResource(R.string.sync_picons_path_long)
                },
                onClick = onEditSyncPiconsPath
            )

            if (showDeveloperCategory) {
                PreferenceCategoryHeader(stringResource(R.string.developer_settings))
                SwitchPreferenceRow(
                    title = stringResource(R.string.developer_settings_enable),
                    summary = null,
                    checked = settings.enableDeveloper,
                    onCheckedChange = { checked ->
                        onChange { it.copy(enableDeveloper = checked) }
                    }
                )
                SwitchPreferenceRow(
                    title = stringResource(R.string.use_fake_picon),
                    summary = stringResource(R.string.use_fake_picon_long),
                    checked = settings.fakePicon,
                    enabled = settings.enableDeveloper,
                    onCheckedChange = { checked -> onChange { it.copy(fakePicon = checked) } }
                )
                SwitchPreferenceRow(
                    title = stringResource(R.string.dump_xml),
                    summary = stringResource(R.string.dump_xml_long),
                    checked = settings.xmlDebug,
                    enabled = settings.enableDeveloper,
                    onCheckedChange = { checked -> onChange { it.copy(xmlDebug = checked) } }
                )
            }
        }
    }

    listDialog?.let { dialog ->
        ListPreferenceDialog(
            spec = dialog,
            onDismiss = { listDialog = null },
            onSelect = { value ->
                onChange { dialog.set(it, value) }
                listDialog = null
            }
        )
    }

    if (syncPiconsPathDraft != null) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.sync_picons_path),
            state = syncPiconsPathDraft,
            onDismiss = onDismissSyncPiconsPath,
            onConfirm = onConfirmSyncPiconsPath
        )
    }
}
