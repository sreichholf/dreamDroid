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
import net.reichholf.dreamdroid.data.CacheRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

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
    val editingSyncPiconsPath: Boolean = false,
    val userMessage: UiText? = null,
    val effect: SettingsEffect? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.settings)
}

/**
 * Phone and TV settings. Values live in [SettingsRepository]; the open picon path
 * dialog and its text survive process death in the [SavedStateHandle].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settings: SettingsRepository,
    private val cache: CacheRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            settings = settings.current(),
            editingSyncPiconsPath = savedStateHandle[KEY_EDITING_SYNC_PATH] ?: false
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val syncPiconsPath = SavedTextField(viewModelScope, savedStateHandle, KEY_SYNC_PATH)

    init {
        viewModelScope.launch {
            settings.settings.collect { values ->
                _uiState.update { it.copy(settings = values) }
            }
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

    fun resetCache(allProfiles: Boolean) {
        viewModelScope.launch {
            cache.clearUseDrivenCache(allProfiles)
            _uiState.update { it.copy(userMessage = UiText.Resource(R.string.reset_cache_done)) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun setEditingSyncPiconsPath(editing: Boolean) {
        savedStateHandle[KEY_EDITING_SYNC_PATH] = editing
        _uiState.update { it.copy(editingSyncPiconsPath = editing) }
    }

    private companion object {
        const val KEY_SYNC_PATH = "settings_sync_picons_path"
        const val KEY_EDITING_SYNC_PATH = "settings_editing_sync_picons_path"
    }
}
