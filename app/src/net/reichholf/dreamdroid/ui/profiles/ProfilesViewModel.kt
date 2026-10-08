package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The saved profiles and receiver discovery. [discovered] is the pick list of the last
 * search while its dialog is open; [discoveryFailed] means that search found nothing.
 */
data class ProfilesUiState(
    val profiles: List<ProfileListItem> = emptyList(),
    val detecting: Boolean = false,
    val discovered: List<Profile>? = null,
    val discoveryFailed: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.profiles)
}

/**
 * Profile list, activation, and discovery. Jobs run on [viewModelScope], so leaving
 * the destination for the editor does not cancel them.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val discovery: ReceiverDiscovery
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    private var loaded: List<Profile> = emptyList()
    private var detected: List<Profile>? = null
    private var refreshJob: Job? = null
    private var detectJob: Job? = null

    /** Reads the list again. The destination calls this on entry, also after the editor. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val (rows, activeId) = withContext(Dispatchers.IO) {
                profiles.profiles() to profiles.activeProfileId()
            }
            loaded = rows
            _uiState.update { state ->
                state.copy(
                    profiles = rows.map { profile ->
                        ProfileListItem(
                            id = profile.id ?: 0,
                            name = profile.name.orEmpty(),
                            host = profile.host.orEmpty(),
                            active = profile.id != null && profile.id == activeId
                        )
                    }
                )
            }
        }
    }

    fun activate(item: ProfileListItem) {
        val profile = profileFor(item) ?: return
        viewModelScope.launch {
            val activated = withContext(Dispatchers.IO) {
                profiles.setCurrent(item.id, forceEvent = true)
            }
            val label = if (activated) {
                R.string.profile_activated
            } else {
                R.string.profile_not_activated
            }
            _uiState.update { it.copy(userMessage = namedMessage(label, profile.name)) }
            refresh()
        }
    }

    fun profileFor(item: ProfileListItem): Profile? = loaded.firstOrNull { it.id == item.id }

    /** Searches the network, or shows the last non-empty result again. */
    fun detectDevices() {
        if (detectJob?.isActive == true) {
            return
        }
        val cached = detected
        if (!cached.isNullOrEmpty()) {
            _uiState.update { it.copy(discovered = cached) }
            return
        }
        _uiState.update { it.copy(detecting = true) }
        detectJob = viewModelScope.launch {
            val found = discovery.find()
            detected = found
            _uiState.update {
                it.copy(
                    detecting = false,
                    discovered = found.takeIf { list -> list.isNotEmpty() },
                    discoveryFailed = found.isEmpty()
                )
            }
        }
    }

    fun reloadDetectedDevices() {
        detected = null
        dismissDiscovery()
        detectDevices()
    }

    fun dismissDiscovery() {
        _uiState.update { it.copy(discovered = null, discoveryFailed = false) }
    }

    /** Saves every discovered receiver as a profile. */
    fun addAllDetected() {
        val found = detected ?: return
        dismissDiscovery()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                found.forEach {
                    profiles.applyDefaultPiconSettings(it)
                    profiles.save(it)
                }
            }
            val names = found.joinToString("', '") { it.name.orEmpty() }
            _uiState.update {
                it.copy(userMessage = namedMessage(R.string.profile_added, names))
            }
            refresh()
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
