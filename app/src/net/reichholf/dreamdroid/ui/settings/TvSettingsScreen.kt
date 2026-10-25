package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.ExternalPlayer
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.TvListRow
import net.reichholf.dreamdroid.ui.compose.TvSwitchRow
import net.reichholf.dreamdroid.ui.dialogs.TextInputDialog
import net.reichholf.dreamdroid.video.Engine

/**
 * TV settings subset matching [R.xml.preferences] on television
 * (`xml-television/preferences.xml`). Same PreferenceManager keys as phone.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onSyncPicons: () -> Unit = {},
    externalPlayers: List<ExternalPlayer> = emptyList(),
    media3Unsupported: List<String> = emptyList(),
    showDeveloperCategory: Boolean = false,
    syncPiconsPathDraft: TextFieldState? = null,
    onEditSyncPiconsPath: () -> Unit = {},
    onConfirmSyncPiconsPath: () -> Unit = {},
    onDismissSyncPiconsPath: () -> Unit = {},
    preferredAudioLanguageDraft: TextFieldState? = null,
    onEditPreferredAudioLanguage: () -> Unit = {},
    onConfirmPreferredAudioLanguage: () -> Unit = {},
    onDismissPreferredAudioLanguage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var listDialog by remember { mutableStateOf<ListDialogSpec?>(null) }
    val firstPreferenceFocus = remember { FocusRequester() }

    val hwEntries = stringArrayResource(R.array.hw_accel_entries)
    val hwValues = stringArrayResource(R.array.hw_accel_values)
    val hwAccelDialogTitle = stringResource(R.string.video_use_hw_accel)

    LaunchedEffect(Unit) {
        firstPreferenceFocus.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreferenceCategoryHeader(stringResource(R.string.video_player))
            TvSwitchRow(
                title = stringResource(R.string.integrated_video_player),
                summary = stringResource(R.string.integrated_video_player_long),
                checked = settings.integratedVideoPlayer,
                modifier = Modifier
                    .focusRequester(firstPreferenceFocus)
                    .testTag("tv_setting_integrated_video_player"),
                onCheckedChange = { checked ->
                    onChange { it.copy(integratedVideoPlayer = checked) }
                }
            )
            ExternalPlayerPreferenceRow(settings, externalPlayers) { listDialog = it }
            PlayerEnginePreferenceRow(settings, media3Unsupported) { listDialog = it }
            TvListRow(
                title = stringResource(R.string.use_hw_accel),
                summary = stringResource(
                    R.string.use_hw_accel_long,
                    entryLabel(hwEntries, hwValues, settings.videoHardwareAcceleration)
                ),
                enabled = settings.integratedVideoPlayer && settings.engine == Engine.Vlc,
                onClick = {
                    listDialog = ListDialogSpec(
                        title = hwAccelDialogTitle,
                        entries = hwEntries.toList(),
                        values = hwValues.toList(),
                        selectedValue = settings.videoHardwareAcceleration,
                        set = { copy(videoHardwareAcceleration = it) }
                    )
                },
                testTag = "tv_setting_video_hardware_acceleration"
            )
            StreamBufferPreferenceRow(settings) { listDialog = it }
            TvListRow(
                title = stringResource(R.string.preferred_audio_language),
                summary = settings.preferredAudioLanguage.ifBlank {
                    stringResource(R.string.audio_language_default)
                },
                onClick = onEditPreferredAudioLanguage,
                enabled = settings.integratedVideoPlayer,
                testTag = "tv_setting_preferred_audio_language"
            )

            PreferenceCategoryHeader(stringResource(R.string.usability))
            TvSwitchRow(
                title = stringResource(R.string.auto_start_last_stream),
                summary = stringResource(R.string.auto_start_last_stream_long),
                checked = settings.autoStartLastStream,
                enabled = settings.integratedVideoPlayer,
                onCheckedChange = { checked ->
                    onChange { it.copy(autoStartLastStream = checked) }
                },
                modifier = Modifier.testTag("tv_setting_auto_start_last_stream")
            )

            PreferenceCategoryHeader(stringResource(R.string.picons))
            TvText(
                text = stringResource(R.string.picons_profiles_hint),
                style = TvMaterialTheme.typography.bodySmall,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ListRowHorizontalInset, vertical = 4.dp)
            )
            TvSwitchRow(
                title = stringResource(R.string.use_name_as_picon_filename),
                summary = stringResource(R.string.use_name_as_picon_filename_long),
                checked = settings.useNameAsPiconFilename,
                onCheckedChange = { checked ->
                    onChange { it.copy(useNameAsPiconFilename = checked) }
                }
            )
            TvListRow(
                title = stringResource(R.string.sync_picons),
                summary = stringResource(R.string.sync_picons_long),
                onClick = onSyncPicons,
                testTag = "tv_setting_sync_picons"
            )
            TvListRow(
                title = stringResource(R.string.sync_picons_path),
                summary = settings.syncPiconsPath.ifEmpty {
                    stringResource(R.string.sync_picons_path_long)
                },
                onClick = onEditSyncPiconsPath,
                testTag = "tv_setting_sync_picons_path"
            )

            if (showDeveloperCategory) {
                PreferenceCategoryHeader(stringResource(R.string.developer_settings))
                TvSwitchRow(
                    title = stringResource(R.string.developer_settings_enable),
                    summary = null,
                    checked = settings.enableDeveloper,
                    onCheckedChange = { checked ->
                        onChange { it.copy(enableDeveloper = checked) }
                    }
                )
                TvSwitchRow(
                    title = stringResource(R.string.use_fake_picon),
                    summary = stringResource(R.string.use_fake_picon_long),
                    checked = settings.fakePicon,
                    enabled = settings.enableDeveloper,
                    onCheckedChange = { checked -> onChange { it.copy(fakePicon = checked) } }
                )
                TvSwitchRow(
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
        TextInputDialog(
            title = stringResource(R.string.sync_picons_path),
            state = syncPiconsPathDraft,
            onDismiss = onDismissSyncPiconsPath,
            onConfirm = onConfirmSyncPiconsPath
        )
    }

    if (preferredAudioLanguageDraft != null) {
        TextInputDialog(
            title = stringResource(R.string.preferred_audio_language),
            state = preferredAudioLanguageDraft,
            supportingText = stringResource(R.string.preferred_audio_language_long),
            onDismiss = onDismissPreferredAudioLanguage,
            onConfirm = onConfirmPreferredAudioLanguage
        )
    }
}
