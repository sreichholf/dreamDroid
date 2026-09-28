package net.reichholf.dreamdroid.tv.ui

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
import net.reichholf.dreamdroid.ui.profiles.ProfileForm
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * [page] and, on Add or Edit, the open [form]. [event] is the last activation, save,
 * or delete, until the host has applied its result policy.
 */
data class TvProfilesUiState(
    val page: TvProfilesPage = TvProfilesPage.List,
    val profiles: List<ProfileListItem> = emptyList(),
    val form: ProfileForm? = null,
    val hostError: UiText? = null,
    val event: TvProfilesEvent? = null
)

/**
 * List, add, edit, and delete for [TvProfilesHost]. The open draft lives here, so it
 * outlives the host's composition; process death drops it.
 */
@HiltViewModel
class TvProfilesHostViewModel @Inject constructor(private val profiles: ProfileRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(TvProfilesUiState())
    val uiState: StateFlow<TvProfilesUiState> = _uiState.asStateFlow()

    private var loaded: List<Profile> = emptyList()
    private var editing: Profile? = null
    private var loadJob: Job? = null
    private var editJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (rows, activeId) = withContext(Dispatchers.IO) {
                profiles.profiles() to profiles.activeProfileId()
            }
            loaded = rows
            _uiState.update { it.copy(profiles = tvProfileRows(rows, activeId)) }
        }
    }

    fun showList() {
        editJob?.cancel()
        editJob = null
        editing = null
        _uiState.update { it.copy(page = TvProfilesPage.List, form = null, hostError = null) }
        reload()
    }

    /** Opens add with [Profile.getDefault]. Keeps the draft when add is already open. */
    fun showAdd() {
        if (_uiState.value.page is TvProfilesPage.Add && editing != null) {
            return
        }
        editJob?.cancel()
        editJob = null
        open(Profile.getDefault(), TvProfilesPage.Add)
    }

    /** Loads [profileId] for editing. Keeps the draft when that edit is already open. */
    fun showEdit(profileId: Int) {
        val page = _uiState.value.page
        if (page is TvProfilesPage.Edit && page.profileId == profileId && editing != null) {
            return
        }
        editJob?.cancel()
        editJob = viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { profiles.profile(profileId) }
            if (profile == null) {
                showList()
            } else {
                open(profile, TvProfilesPage.Edit(profileId))
            }
        }
    }

    fun onFormChange(form: ProfileForm) {
        _uiState.update {
            val hostError = if (form.host == it.form?.host) it.hostError else null
            it.copy(form = form, hostError = hostError)
        }
    }

    fun activate(id: Int) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { profiles.setCurrent(id, forceEvent = true) }
            _uiState.update { it.copy(event = TvProfilesEvent.Activate(success)) }
        }
    }

    /**
     * Saves the open draft. Saving the active profile reloads it with a switch event,
     * so the hub reconnects with the new settings.
     */
    fun save() {
        val profile = editing ?: return
        val form = _uiState.value.form ?: return
        if (form.host.isBlank()) {
            _uiState.update {
                it.copy(
                    hostError = UiText.Resource(R.string.host_empty),
                    event = TvProfilesEvent.Save(saved = false, currentProfile = false)
                )
            }
            return
        }
        form.applyTo(profile)
        if (profile.streamHost == null) {
            profile.streamHost = ""
        }
        viewModelScope.launch {
            val isCurrent = withContext(Dispatchers.IO) {
                profiles.save(profile)
                val current = profile.id != null && profile.id == profiles.current.value?.id
                if (current) {
                    profiles.reloadCurrent()
                }
                current
            }
            _uiState.update {
                it.copy(event = TvProfilesEvent.Save(saved = true, currentProfile = isCurrent))
            }
            showList()
        }
    }

    fun delete(id: Int) {
        val profile = loaded.firstOrNull { (it.id ?: 0) == id } ?: return
        viewModelScope.launch {
            val deletingCurrent = withContext(Dispatchers.IO) {
                val current = profile.id != null && profile.id == profiles.current.value?.id
                profiles.delete(profile)
                current
            }
            _uiState.update { it.copy(event = TvProfilesEvent.Delete(deletingCurrent)) }
            if (!deletingCurrent) {
                reload()
            }
        }
    }

    fun onEventHandled() {
        _uiState.update { it.copy(event = null) }
    }

    private fun open(profile: Profile, page: TvProfilesPage) {
        editing = profile
        _uiState.update {
            it.copy(page = page, form = ProfileForm.from(profile), hostError = null)
        }
    }
}

private fun tvProfileRows(profiles: List<Profile>, activeId: Int?): List<ProfileListItem> =
    profiles.map { profile ->
        val id = profile.id ?: 0
        ProfileListItem(
            id = id,
            name = profile.name.orEmpty(),
            host = profile.host.orEmpty(),
            active = id > 0 && id == activeId
        )
    }
