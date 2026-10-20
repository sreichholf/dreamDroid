package net.reichholf.dreamdroid.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.ExternalPlayer
import net.reichholf.dreamdroid.data.ExternalPlayers
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.helpers.PiconSyncScheduler
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.video.Media3Formats

/** What a settings change asks of the activity. The destination runs it, then clears it. */
enum class SettingsEffect {
    /** The day/night theme changed. */
    ApplyTheme,

    /** Dynamic colors changed; they apply on the next start. */
    Restart
}

/**
 * [editingSyncPiconsPath] is true while the picon path dialog is open; its text is
 * [SettingsViewModel.syncPiconsPath].
 */
data class SettingsUiState(
    val settings: AppSettings,
    val externalPlayers: List<ExternalPlayer> = emptyList(),
    /** Receiver formats Media3 cannot decode on this device. */
    val media3Unsupported: List<String> = emptyList(),
    val editingSyncPiconsPath: Boolean = false,
    val editingPreferredAudioLanguage: Boolean = false,
    val userMessage: UiText? = null,
    val effect: SettingsEffect? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.settings)
}

/**
 * Phone and TV settings. Values live in [SettingsRepository]; open text dialogs and drafts
 * survive process death in the [SavedStateHandle].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settings: SettingsRepository,
    private val services: ServiceRepository,
    private val piconSync: PiconSyncScheduler,
    private val externalPlayers: ExternalPlayers,
    private val media3Formats: Media3Formats
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            settings = settings.current(),
            editingSyncPiconsPath = savedStateHandle[KEY_EDITING_SYNC_PATH] ?: false,
            editingPreferredAudioLanguage =
                savedStateHandle[KEY_EDITING_PREFERRED_AUDIO_LANGUAGE] ?: false
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val syncPiconsPath = SavedTextField(viewModelScope, savedStateHandle, KEY_SYNC_PATH)
    val preferredAudioLanguage = SavedTextField(
        viewModelScope,
        savedStateHandle,
        KEY_PREFERRED_AUDIO_LANGUAGE
    )

    init {
        viewModelScope.launch {
            settings.settings.collect { values ->
                _uiState.update { it.copy(settings = values) }
            }
        }
        viewModelScope.launch {
            val players = externalPlayers.installed()
            _uiState.update { it.copy(externalPlayers = players) }
        }
        viewModelScope.launch {
            val unsupported = media3Formats.unsupported()
            _uiState.update { it.copy(media3Unsupported = unsupported) }
        }
    }

    /** Writes the settings [transform] changes. */
    fun update(transform: (AppSettings) -> AppSettings) {
        val (before, after) = settings.update(transform)
        val effect = when {
            after.themeType != before.themeType -> SettingsEffect.ApplyTheme
            after.dynamicThemeColors != before.dynamicThemeColors -> SettingsEffect.Restart
            else -> null
        }
        _uiState.update { state ->
            state.copy(settings = after, effect = effect ?: state.effect)
        }
    }

    fun onEffectHandled() {
        _uiState.update { it.copy(effect = null) }
    }

    fun editSyncPiconsPath() {
        syncPiconsPath.set(_uiState.value.settings.syncPiconsPath)
        setEditingSyncPiconsPath(true)
    }

    fun confirmSyncPiconsPath() {
        val path = syncPiconsPath.text
        setEditingSyncPiconsPath(false)
        update { it.copy(syncPiconsPath = path) }
    }

    fun dismissSyncPiconsPath() {
        setEditingSyncPiconsPath(false)
    }

    fun editPreferredAudioLanguage() {
        preferredAudioLanguage.set(_uiState.value.settings.preferredAudioLanguage)
        setEditingPreferredAudioLanguage(true)
    }

    fun confirmPreferredAudioLanguage() {
        val language = preferredAudioLanguage.text.trim()
        setEditingPreferredAudioLanguage(false)
        update { it.copy(preferredAudioLanguage = language) }
    }

    fun dismissPreferredAudioLanguage() {
        setEditingPreferredAudioLanguage(false)
    }

    fun resetCache(allProfiles: Boolean) {
        viewModelScope.launch {
            services.clearUseDrivenCache(allProfiles)
            _uiState.update { it.copy(userMessage = UiText.Resource(R.string.reset_cache_done)) }
        }
    }

    /** Starts the picon sync, or says that one is still running. */
    fun syncPicons() {
        viewModelScope.launch {
            val message = if (piconSync.enqueue()) {
                R.string.picon_sync_started
            } else {
                R.string.picon_sync_running
            }
            _uiState.update { it.copy(userMessage = UiText.Resource(message)) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun setEditingSyncPiconsPath(editing: Boolean) {
        savedStateHandle[KEY_EDITING_SYNC_PATH] = editing
        _uiState.update { it.copy(editingSyncPiconsPath = editing) }
    }

    private fun setEditingPreferredAudioLanguage(editing: Boolean) {
        savedStateHandle[KEY_EDITING_PREFERRED_AUDIO_LANGUAGE] = editing
        _uiState.update { it.copy(editingPreferredAudioLanguage = editing) }
    }

    private companion object {
        const val KEY_SYNC_PATH = "settings_sync_picons_path"
        const val KEY_EDITING_SYNC_PATH = "settings_editing_sync_picons_path"
        const val KEY_PREFERRED_AUDIO_LANGUAGE = "settings_preferred_audio_language"
        const val KEY_EDITING_PREFERRED_AUDIO_LANGUAGE =
            "settings_editing_preferred_audio_language"
    }
}
