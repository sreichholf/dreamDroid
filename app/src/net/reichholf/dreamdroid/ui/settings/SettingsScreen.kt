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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowItemColors
import net.reichholf.dreamdroid.ui.dialogs.SimpleChoiceAlertDialog
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

@Composable
fun SettingsScreen(
    state: SettingsState,
    onThemeChanged: () -> Unit,
    onDynamicColorsChanged: () -> Unit,
    onSyncPicons: () -> Unit,
    onAbout: () -> Unit = {},
    onChangelog: () -> Unit = {},
    onBackup: () -> Unit = {},
    onResetCache: (allProfiles: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var listDialog by remember { mutableStateOf<ListDialogSpec?>(null) }
    var editDialog by remember { mutableStateOf<EditDialogSpec?>(null) }
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
    val syncPathDialogTitle = stringResource(R.string.sync_picons_path)

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
            checked = state.integratedVideoPlayer,
            onCheckedChange = {
                state.setBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, it)
            }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.video_enable_gestures),
            summary = stringResource(R.string.video_enable_gestures_long),
            checked = state.videoEnableGestures,
            enabled = state.integratedVideoPlayer,
            onCheckedChange = {
                state.setBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, it)
            }
        )
        ListPreferenceRow(
            title = stringResource(R.string.use_hw_accel),
            summary = stringResource(
                R.string.use_hw_accel_long,
                entryLabel(hwEntries, hwValues, state.videoHardwareAcceleration)
            ),
            enabled = state.integratedVideoPlayer,
            onClick = {
                listDialog = ListDialogSpec(
                    title = hwAccelDialogTitle,
                    entries = hwEntries.toList(),
                    values = hwValues.toList(),
                    selectedValue = state.videoHardwareAcceleration,
                    key = DreamDroid.PREFS_KEY_HWACCEL
                )
            }
        )

        PreferenceCategoryHeader(stringResource(R.string.usability))
        val startEntries = stringArrayResource(R.array.start_screen_entries)
        val startValues = stringArrayResource(R.array.start_screen_values)
        val startScreenTitle = stringResource(R.string.start_screen)
        ListPreferenceRow(
            title = startScreenTitle,
            summary = entryLabel(startEntries, startValues, state.startScreen),
            onClick = {
                listDialog = ListDialogSpec(
                    title = startScreenTitle,
                    entries = startEntries.toList(),
                    values = startValues.toList(),
                    selectedValue = state.startScreen,
                    key = DreamDroid.PREFS_KEY_START_SCREEN
                )
            }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_volume_control),
            summary = stringResource(R.string.enable_volume_control_long),
            checked = state.volumeControl,
            onCheckedChange = { state.setBoolean(SettingsState.KEY_VOLUME_CONTROL, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_instant_zap),
            summary = stringResource(R.string.enable_instant_zap_long),
            checked = state.instantZap,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.show_now_playing_strip),
            summary = stringResource(R.string.show_now_playing_strip_long),
            checked = state.nowPlayingStrip,
            onCheckedChange = {
                state.setBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, it)
            }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.default_to_full_vrm),
            summary = stringResource(R.string.default_to_full_vrm_long),
            checked = state.simpleVrm,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_SIMPLE_VRM, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.mobile_imdb),
            summary = stringResource(R.string.mobile_imdb_long),
            checked = state.mobileImdb,
            onCheckedChange = { state.setBoolean(SettingsState.KEY_MOBILE_IMDB, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.confirm_app_close),
            summary = stringResource(R.string.confirm_app_close_long),
            checked = state.confirmAppClose,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.play_button_as_play_pause),
            summary = stringResource(R.string.play_button_as_play_pause_long),
            checked = state.playButtonAsPlayPause,
            onCheckedChange = {
                state.setBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, it)
            }
        )

        PreferenceCategoryHeader(stringResource(R.string.appearance))
        ListPreferenceRow(
            title = stringResource(R.string.theme_long),
            summary = entryLabel(themeEntries, themeValues, state.themeType),
            onClick = {
                listDialog = ListDialogSpec(
                    title = themeDialogTitle,
                    entries = themeEntries.toList(),
                    values = themeValues.toList(),
                    selectedValue = state.themeType,
                    key = DreamDroid.PREFS_KEY_THEME_TYPE
                )
            }
        )
        if (state.showDynamicThemeColors) {
            SwitchPreferenceRow(
                title = stringResource(R.string.dynamic_theme_colors),
                summary = stringResource(R.string.dynamic_theme_colors_long),
                checked = state.dynamicThemeColors,
                onCheckedChange = {
                    state.setBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, it)
                    onDynamicColorsChanged()
                }
            )
        }
        SwitchPreferenceRow(
            title = stringResource(R.string.enable_animations),
            summary = stringResource(R.string.enable_animations_long),
            checked = state.enableAnimations,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, it) }
        )
        ListPreferenceRow(
            title = stringResource(R.string.max_grid_cols),
            summary = stringResource(R.string.max_grid_cols_long),
            onClick = {
                listDialog = ListDialogSpec(
                    title = gridDialogTitle,
                    entries = gridEntries.toList(),
                    values = gridValues.toList(),
                    selectedValue = state.gridMaxCols,
                    key = DreamDroid.PREFS_KEY_GRID_MAX_COLS
                )
            }
        )
        ListPreferenceRow(
            title = multiEpgTextSizeTitle,
            summary = entryLabel(
                multiEpgTextSizeEntries,
                multiEpgTextSizeValues,
                state.multiEpgTextSize
            ),
            onClick = {
                listDialog = ListDialogSpec(
                    title = multiEpgTextSizeTitle,
                    entries = multiEpgTextSizeEntries.toList(),
                    values = multiEpgTextSizeValues.toList(),
                    selectedValue = state.multiEpgTextSize,
                    key = DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE
                )
            }
        )

        PreferenceCategoryHeader(stringResource(R.string.picons))
        SwitchPreferenceRow(
            title = stringResource(R.string.use_picons),
            summary = stringResource(R.string.use_picons_long),
            checked = state.picons,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.online_picons),
            summary = stringResource(R.string.online_picons_long),
            checked = state.piconsOnline,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, it) }
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.use_name_as_picon_filename),
            summary = stringResource(R.string.use_name_as_picon_filename_long),
            checked = state.useNameAsPiconFilename,
            enabled = state.picons,
            onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, it) }
        )
        ActionPreferenceRow(
            title = stringResource(R.string.sync_picons),
            summary = stringResource(R.string.sync_picons_long),
            enabled = state.picons,
            onClick = onSyncPicons
        )
        ActionPreferenceRow(
            title = stringResource(R.string.sync_picons_path),
            summary = stringResource(R.string.sync_picons_path_long),
            enabled = state.picons,
            onClick = {
                editDialog = EditDialogSpec(
                    title = syncPathDialogTitle,
                    value = state.syncPiconsPath,
                    key = DreamDroid.PREFS_KEY_SYNC_PICONS_PATH
                )
            }
        )

        if (state.showDeveloperCategory) {
            PreferenceCategoryHeader(stringResource(R.string.developer_settings))
            SwitchPreferenceRow(
                title = stringResource(R.string.developer_settings_enable),
                summary = null,
                checked = state.enableDeveloper,
                onCheckedChange = {
                    state.setBoolean(DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS, it)
                }
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.use_fake_picon),
                summary = stringResource(R.string.use_fake_picon_long),
                checked = state.fakePicon,
                enabled = state.enableDeveloper,
                onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, it) }
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.dump_xml),
                summary = stringResource(R.string.dump_xml_long),
                checked = state.xmlDebug,
                enabled = state.enableDeveloper,
                onCheckedChange = { state.setBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, it) }
            )
        }

        PreferenceCategoryHeader(stringResource(R.string.profile))
        SwitchPreferenceRow(
            title = stringResource(R.string.auto_switch_profile_wifi_based),
            summary = stringResource(R.string.auto_switch_profile_wifi_based_long),
            checked = state.autoSwitchProfileWifiBased,
            onCheckedChange = {
                state.setBoolean(DreamDroid.PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED, it)
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
                state.setString(dialog.key, value)
                if (dialog.key == DreamDroid.PREFS_KEY_THEME_TYPE) {
                    onThemeChanged()
                }
                listDialog = null
            }
        )
    }

    editDialog?.let { dialog ->
        EditTextPreferenceDialog(
            spec = dialog,
            onDismiss = { editDialog = null },
            onConfirm = { value ->
                state.setString(dialog.key, value)
                editDialog = null
            }
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
    val key: String
)

internal data class EditDialogSpec(val title: String, val value: String, val key: String)

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

@Composable
internal fun EditTextPreferenceDialog(
    spec: EditDialogSpec,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(spec.value) { mutableStateOf(spec.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
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

fun ComposeView.bindSettingsScreen(
    state: SettingsState,
    onThemeChanged: () -> Unit,
    onDynamicColorsChanged: () -> Unit,
    onSyncPicons: () -> Unit,
    onAbout: () -> Unit,
    onChangelog: () -> Unit,
    onBackup: () -> Unit,
    onResetCache: (allProfiles: Boolean) -> Unit = {}
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            SettingsScreen(
                state = state,
                onThemeChanged = onThemeChanged,
                onDynamicColorsChanged = onDynamicColorsChanged,
                onSyncPicons = onSyncPicons,
                onAbout = onAbout,
                onChangelog = onChangelog,
                onBackup = onBackup,
                onResetCache = onResetCache
            )
        }
    }
}
