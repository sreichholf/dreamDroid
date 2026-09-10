package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R

/**
 * TV settings subset matching [R.xml.preferences] on television
 * (`xml-television/preferences.xml`). Same PreferenceManager keys as phone.
 */
@Composable
fun TvSettingsScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
) {
    var listDialog by remember { mutableStateOf<ListDialogSpec?>(null) }
    var editDialog by remember { mutableStateOf<EditDialogSpec?>(null) }

    val hwEntries = stringArrayResource(R.array.hw_accel_entries)
    val hwValues = stringArrayResource(R.array.hw_accel_values)
    val hwAccelDialogTitle = stringResource(R.string.video_use_hw_accel)
    val syncPathDialogTitle = stringResource(R.string.sync_picons_path)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        PreferenceCategoryHeader(stringResource(R.string.video_player))
        ListPreferenceRow(
            title = stringResource(R.string.use_hw_accel),
            summary = stringResource(
                R.string.use_hw_accel_long,
                entryLabel(hwEntries, hwValues, state.videoHardwareAcceleration),
            ),
            onClick = {
                listDialog = ListDialogSpec(
                    title = hwAccelDialogTitle,
                    entries = hwEntries.toList(),
                    values = hwValues.toList(),
                    selectedValue = state.videoHardwareAcceleration,
                    key = DreamDroid.PREFS_KEY_HWACCEL,
                )
            },
        )

        PreferenceCategoryHeader(stringResource(R.string.picons))
        SwitchPreferenceRow(
            title = stringResource(R.string.use_name_as_picon_filename),
            summary = stringResource(R.string.use_name_as_picon_filename_long),
            checked = state.useNameAsPiconFilename,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, it) },
        )
        ActionPreferenceRow(
            title = stringResource(R.string.sync_picons_path),
            summary = state.syncPiconsPath.ifEmpty {
                stringResource(R.string.sync_picons_path_long)
            },
            onClick = {
                editDialog = EditDialogSpec(
                    title = syncPathDialogTitle,
                    value = state.syncPiconsPath,
                    key = DreamDroid.PREFS_KEY_SYNC_PICONS_PATH,
                )
            },
        )

        if (state.showDeveloperCategory) {
            PreferenceCategoryHeader(stringResource(R.string.developer_settings))
            SwitchPreferenceRow(
                title = stringResource(R.string.developer_settings_enable),
                summary = null,
                checked = state.enableDeveloper,
                onCheckedChange = {
                    state.setBoolean(DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS, it)
                },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.use_fake_picon),
                summary = stringResource(R.string.use_fake_picon_long),
                checked = state.fakePicon,
                enabled = state.enableDeveloper,
                onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.dump_xml),
                summary = stringResource(R.string.dump_xml_long),
                checked = state.xmlDebug,
                enabled = state.enableDeveloper,
                onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, it) },
            )
        }
    }

    listDialog?.let { dialog ->
        ListPreferenceDialog(
            spec = dialog,
            onDismiss = { listDialog = null },
            onSelect = { value ->
                state.setString(dialog.key, value)
                listDialog = null
            },
        )
    }

    editDialog?.let { dialog ->
        EditTextPreferenceDialog(
            spec = dialog,
            onDismiss = { editDialog = null },
            onConfirm = { value ->
                state.setString(dialog.key, value)
                editDialog = null
            },
        )
    }
}
