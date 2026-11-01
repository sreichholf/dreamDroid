package net.reichholf.dreamdroid.ui.profiles

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * One profile's bouquet visibility. [hidden] holds the references the profile hides;
 * every bouquet not in it is shown, so a bouquet the receiver adds later is visible
 * without the user doing anything. The list keeps hidden bouquets, with their switch off,
 * so they can be shown again.
 */
data class ProfileBouquetsUiState(
    val profileId: Int = -1,
    val profileName: String = "",
    val tv: List<Service> = emptyList(),
    val radio: List<Service> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: UiText? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.bouquets)
}

/**
 * Reads a profile and its TV and radio bouquets, then saves each visibility change to that
 * profile. The profile need not be active: editing another profile never switches to it.
 */
@HiltViewModel
class ProfileBouquetsViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val services: ServiceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileBouquetsUiState())
    val uiState: StateFlow<ProfileBouquetsUiState> = _uiState.asStateFlow()

    private var profile: Profile? = null
    private var loadJob: Job? = null

    /** Serializes the saves, so two quick toggles land in the order the user tapped them. */
    private val saves = Mutex()

    /** Loads [profileId] and reads its bouquets again, as after returning to the screen. */
    fun load(profileId: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(profileId = profileId, loading = true, error = null)
            }
            val loaded = withContext(Dispatchers.IO) { profiles.profile(profileId) }
            if (loaded == null) {
                profile = null
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = UiText.Resource(R.string.profile_not_found)
                    )
                }
                return@launch
            }
            profile = loaded
            val response = withContext(Dispatchers.IO) { services.bouquetsForVisibility(loaded) }
            val bouquets = response.value
            _uiState.update { state ->
                val hidden = loaded.hiddenBouquets()
                if (bouquets == null) {
                    state.copy(
                        profileName = loaded.name.orEmpty(),
                        hidden = hidden,
                        loading = false,
                        error = response.error.contentErrorText()
                    )
                } else {
                    state.copy(
                        profileName = loaded.name.orEmpty(),
                        tv = bouquets.tv,
                        radio = bouquets.radio,
                        hidden = hidden,
                        loading = false,
                        error = null
                    )
                }
            }
        }
    }

    /** Asks the receiver again, as after a failure or a change on the box. */
    fun reload() {
        load(_uiState.value.profileId)
    }

    /** Shows or hides [service] for this profile and saves it at once. */
    fun setVisible(service: Service, visible: Boolean) {
        val next = if (visible) {
            _uiState.value.hidden - service.reference
        } else {
            _uiState.value.hidden + service.reference
        }
        saveHidden(next, message = null)
    }

    /** Shows every bouquet again, including ones the receiver no longer lists. */
    fun showAll() {
        saveHidden(emptySet(), message = UiText.Resource(R.string.bouquets_all_shown))
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun saveHidden(next: Set<String>, message: UiText?) {
        val target = profile ?: return
        val before = _uiState.value.hidden
        if (next == before) {
            return
        }
        _uiState.update { it.copy(hidden = next) }
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    saves.withLock {
                        target.setHiddenBouquets(next)
                        profiles.save(target)
                    }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(DreamDroid.LOG_TAG, "Could not save the profile's hidden bouquets", e)
                false
            }
            if (saved) {
                _uiState.update { it.copy(userMessage = message) }
            } else {
                target.setHiddenBouquets(before)
                _uiState.update {
                    it.copy(
                        hidden = if (it.hidden == next) before else it.hidden,
                        userMessage = UiText.Resource(R.string.bouquets_save_failed)
                    )
                }
            }
        }
    }
}
