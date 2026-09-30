package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors
import net.reichholf.dreamdroid.ui.dialogs.SimpleChoiceAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.TextInputDialog

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onSyncPicons: () -> Unit,
    showDeveloperCategory: Boolean = false,
    showDynamicThemeColors: Boolean = false,
    syncPiconsPathDraft: TextFieldState? = null,
    onEditSyncPiconsPath: () -> Unit = {},
    onConfirmSyncPiconsPath: () -> Unit = {},
    onDismissSyncPiconsPath: () -> Unit = {},
    onAbout: () -> Unit = {},
    onChangelog: () -> Unit = {},
    onBackup: () -> Unit = {},
    onResetCache: (allProfiles: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var listDialog by remember { mutableStateOf<ListDialogSpec?>(null) }
    var resetChoice by remember { mutableStateOf(false) }

    val themeEntries = stringArrayResource(R.array.theme_option_entries)
    val themeValues = stringArrayResource(R.array.theme_option_values)
    val hwEntries = stringArrayResource(R.array.hw_accel_entries)
    val hwValues = stringArrayResource(R.array.hw_accel_values)
    val gridEntries = stringArrayResource(R.array.max_grid_col_entries)
    val gridValues = stringArrayResource(R.array.max_grid_col_values)
    val multiEpgTextSizeEntries = stringArrayResource(R.array.multiepg_text_size_entries)
    val multiEpgTextSizeValues = stringArrayResource(R.array.multiepg_text_size_values)

    val hwAccelDialogTitle = stringResource(R.string.video_use_hw_accel)
    val themeDialogTitle = stringResource(R.string.theme)
    val gridDialogTitle = stringResource(R.string.max_grid_cols)
    val multiEpgTextSizeTitle = stringResource(R.string.multiepg_text_size)

    Column(
        modifier = modifier
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
        SwitchPreferenceRow(
            title = stringResource(R.string.video_enable_gestures),
            summary = stringResource(R.string.video_enable_gestures_long),
            checked = settings.videoEnableGestures,
            enabled = settings.integratedVideoPlayer,
            onCheckedChange = { checked ->
                onChange { it.copy(videoEnableGestures = checked) }
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

        PreferenceCategoryHeader(stringResource(R.string.usability))
        val startEntries = stringArrayResource(R.array.start_screen_entries)
        val startValues = stringArrayResource(R.array.start_screen_values)
        val startScreenTitle = stringResource(R.string.start_screen)
        ListPreferenceRow(
            title = startScreenTitle,
            summary = entryLabel(startEntries, startValues, settings.startScreen),
            onClick = {
                listDialog = ListDialogSpec(
                    title = startScreenTitle,
                    entries = startEntries.toList(),
                    values = startValues.toList(),
                    selectedValue = settings.startScreen,
                    set = { copy(startScreen = it) }
                )
            }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_volume_control),
            summary = stringResource(R.string.enable_volume_control_long),
            checked = settings.volumeControl,
            onCheckedChange = { checked -> onChange { it.copy(volumeControl = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_instant_zap),
            summary = stringResource(R.string.enable_instant_zap_long),
            checked = settings.instantZap,
            onCheckedChange = { checked -> onChange { it.copy(instantZap = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.show_now_playing_strip),
            summary = stringResource(R.string.show_now_playing_strip_long),
            checked = settings.nowPlayingStrip,
            onCheckedChange = { checked ->
                onChange { it.copy(nowPlayingStrip = checked) }
            }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.default_to_full_vrm),
            summary = stringResource(R.string.default_to_full_vrm_long),
            checked = settings.simpleVrm,
            onCheckedChange = { checked -> onChange { it.copy(simpleVrm = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.mobile_imdb),
            summary = stringResource(R.string.mobile_imdb_long),
            checked = settings.mobileImdb,
            onCheckedChange = { checked -> onChange { it.copy(mobileImdb = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.confirm_app_close),
            summary = stringResource(R.string.confirm_app_close_long),
            checked = settings.confirmAppClose,
            onCheckedChange = { checked -> onChange { it.copy(confirmAppClose = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.play_button_as_play_pause),
            summary = stringResource(R.string.play_button_as_play_pause_long),
            checked = settings.playButtonAsPlayPause,
            onCheckedChange = { checked ->
                onChange { it.copy(playButtonAsPlayPause = checked) }
            }
        )

        PreferenceCategoryHeader(stringResource(R.string.appearance))
        ListPreferenceRow(
            title = stringResource(R.string.theme_long),
            summary = entryLabel(themeEntries, themeValues, settings.themeType),
            onClick = {
                listDialog = ListDialogSpec(
                    title = themeDialogTitle,
                    entries = themeEntries.toList(),
                    values = themeValues.toList(),
                    selectedValue = settings.themeType,
                    set = { copy(themeType = it) }
                )
            }
        )
        if (showDynamicThemeColors) {
            SwitchPreferenceRow(
                title = stringResource(R.string.dynamic_theme_colors),
                summary = stringResource(R.string.dynamic_theme_colors_long),
                checked = settings.dynamicThemeColors,
                onCheckedChange = { checked ->
                    onChange { it.copy(dynamicThemeColors = checked) }
                }
            )
        }
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_animations),
            summary = stringResource(R.string.enable_animations_long),
            checked = settings.enableAnimations,
            onCheckedChange = { checked -> onChange { it.copy(enableAnimations = checked) } }
        )
        ListPreferenceRow(
            title = stringResource(R.string.max_grid_cols),
            summary = stringResource(R.string.max_grid_cols_long),
            onClick = {
                listDialog = ListDialogSpec(
                    title = gridDialogTitle,
                    entries = gridEntries.toList(),
                    values = gridValues.toList(),
                    selectedValue = settings.gridMaxCols,
                    set = { copy(gridMaxCols = it) }
                )
            }
        )
        ListPreferenceRow(
            title = multiEpgTextSizeTitle,
            summary = entryLabel(
                multiEpgTextSizeEntries,
                multiEpgTextSizeValues,
                settings.multiEpgTextSize
            ),
            onClick = {
                listDialog = ListDialogSpec(
                    title = multiEpgTextSizeTitle,
                    entries = multiEpgTextSizeEntries.toList(),
                    values = multiEpgTextSizeValues.toList(),
                    selectedValue = settings.multiEpgTextSize,
                    set = { copy(multiEpgTextSize = it) }
                )
            }
        )

        PreferenceCategoryHeader(stringResource(R.string.picons))
        SwitchPreferenceRow(
            title = stringResource(R.string.use_picons),
            summary = stringResource(R.string.use_picons_long),
            checked = settings.picons,
            onCheckedChange = { checked -> onChange { it.copy(picons = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.online_picons),
            summary = stringResource(R.string.online_picons_long),
            checked = settings.piconsOnline,
            onCheckedChange = { checked -> onChange { it.copy(piconsOnline = checked) } }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.use_name_as_picon_filename),
            summary = stringResource(R.string.use_name_as_picon_filename_long),
            checked = settings.useNameAsPiconFilename,
            enabled = settings.picons,
            onCheckedChange = { checked -> onChange { it.copy(useNameAsPiconFilename = checked) } }
        )
        ActionPreferenceRow(
            title = stringResource(R.string.sync_picons),
            summary = stringResource(R.string.sync_picons_long),
            enabled = settings.picons,
            onClick = onSyncPicons
        )
        ActionPreferenceRow(
            title = stringResource(R.string.sync_picons_path),
            summary = stringResource(R.string.sync_picons_path_long),
            enabled = settings.picons,
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

        PreferenceCategoryHeader(stringResource(R.string.profile))
        SwitchPreferenceRow(
            title = stringResource(R.string.auto_switch_profile_wifi_based),
            summary = stringResource(R.string.auto_switch_profile_wifi_based_long),
            checked = settings.autoSwitchProfileWifiBased,
            onCheckedChange = { checked ->
                onChange { it.copy(autoSwitchProfileWifiBased = checked) }
            }
        )

        ActionPreferenceRow(
            title = stringResource(R.string.reset_cache),
            summary = stringResource(R.string.reset_cache_long),
            onClick = { resetChoice = true }
        )

        ActionPreferenceRow(stringResource(R.string.about), DreamDroid.VERSION_STRING, onAbout)
        ActionPreferenceRow(stringResource(R.string.changelog), null, onChangelog)
        ActionPreferenceRow(stringResource(R.string.backup), null, onBackup)
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
        TextInputDialog(
            title = stringResource(R.string.sync_picons_path),
            state = syncPiconsPathDraft,
            onDismiss = onDismissSyncPiconsPath,
            onConfirm = onConfirmSyncPiconsPath
        )
    }

    if (resetChoice) {
        SimpleChoiceAlertDialog(
            title = stringResource(R.string.reset_cache),
            items = listOf(
                stringResource(R.string.reset_cache_current),
                stringResource(R.string.reset_cache_all)
            ),
            onDismiss = { resetChoice = false },
            onChoice = { index -> onResetCache(index == RESET_CACHE_ALL) }
        )
    }
}

internal data class ListDialogSpec(
    val title: String,
    val entries: List<String>,
    val values: List<String>,
    val selectedValue: String,
    val set: AppSettings.(String) -> AppSettings
)

internal const val RESET_CACHE_ALL = 1

@Composable
internal fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = ListRowHorizontalInset + 16.dp,
            end = ListRowHorizontalInset + 16.dp,
            top = 20.dp,
            bottom = 8.dp
        )
    )
}

@Composable
internal fun SwitchPreferenceRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    ListRowSurface {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics(mergeDescendants = true) {}
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                ),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
            },
            supportingContent = if (!summary.isNullOrEmpty()) {
                {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = contentAlpha
                        )
                    )
                }
            } else {
                null
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled
                )
            },
            colors = listRowItemColors()
        )
    }
}

@Composable
internal fun ListPreferenceRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ActionPreferenceRow(title = title, summary = summary, enabled = enabled, onClick = onClick)
}

@Composable
internal fun ActionPreferenceRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    ListRowSurface(modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
    }
}

@Composable
internal fun ListPreferenceDialog(
    spec: ListDialogSpec,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column {
                spec.entries.zip(spec.values).forEach { (entry, value) ->
                    val selected = value == spec.selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null
                        )
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

internal fun entryLabel(entries: Array<String>, values: Array<String>, selected: String): String {
    val idx = values.indexOf(selected)
    return if (idx in entries.indices) entries[idx] else selected
}
