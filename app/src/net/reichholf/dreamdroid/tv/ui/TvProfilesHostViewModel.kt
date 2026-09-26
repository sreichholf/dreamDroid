package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.profiles.ProfileEditState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem

/**
 * List, add, and edit for [TvProfilesHost]. The activity-scoped ViewModel keeps
 * [page] and the open [ProfileEditState] across configuration changes. Process death
 * still drops the draft. Rows load on [viewModelScope] through the suspend profile DAO.
 */
class TvProfilesHostViewModel(application: Application) : AndroidViewModel(application) {
    internal var page by mutableStateOf<TvProfilesPage>(TvProfilesPage.List)
        private set

    var profiles by mutableStateOf<List<ProfileListItem>>(emptyList())
        private set

    var editState by mutableStateOf<ProfileEditState?>(null)
        private set

    var editingProfile by mutableStateOf<Profile?>(null)
        private set

    private var loadedProfiles: List<Profile> = emptyList()
    private var loadJob: Job? = null
    private var editJob: Job? = null
    private var editorRequest: Int = 0

    init {
        reload()
    }

    fun reload() {
        val app = getApplication<Application>()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = AppDatabase.profiles(app).getProfiles()
            if (!isActive) {
                return@launch
            }
            publish(loaded)
        }
    }

    fun showList() {
        editorRequest++
        editJob?.cancel()
        editJob = null
        page = TvProfilesPage.List
        editState = null
        editingProfile = null
        reload()
    }

    /** Opens add with [Profile.getDefault]. Reuses the draft when add is already open. */
    fun showAdd() {
        if (page is TvProfilesPage.Add && editState != null && editingProfile != null) {
            return
        }
        editorRequest++
        editJob?.cancel()
        editJob = null
        val created = Profile.getDefault()
        editingProfile = created
        editState = ProfileEditState.fromProfile(created)
        page = TvProfilesPage.Add
    }

    /**
     * Loads [profileId] on [viewModelScope]. Reuses the draft when that edit is already open.
     */
    fun showEdit(profileId: Int) {
        val current = page
        if (current is TvProfilesPage.Edit &&
            current.profileId == profileId &&
            editState != null &&
            editingProfile != null
        ) {
            return
        }
        val request = ++editorRequest
        editJob?.cancel()
        val app = getApplication<Application>()
        editJob = viewModelScope.launch {
            val profile = AppDatabase.profiles(app).getProfile(profileId)
            if (!isActive || request != editorRequest) {
                return@launch
            }
            if (profile == null) {
                showList()
                return@launch
            }
            editingProfile = profile
            editState = ProfileEditState.fromProfile(profile)
            page = TvProfilesPage.Edit(profileId)
        }
    }

    fun loadedProfile(id: Int): Profile? = loadedProfiles.firstOrNull { (it.id ?: 0) == id }

    private fun publish(loaded: List<Profile>) {
        val app = getApplication<Application>()
        val prefId = PreferenceManager.getDefaultSharedPreferences(app)
            .getInt(DreamDroid.CURRENT_PROFILE, -1)
        val liveId = ProfileRepository.get().current.value?.id ?: -1
        loadedProfiles = loaded.toList()
        profiles = tvProfileRows(loadedProfiles, prefId, liveId)
    }
}

private fun tvProfileRows(
    profiles: List<Profile>,
    prefId: Int,
    liveId: Int
): List<ProfileListItem> {
    val activeId = if (prefId > -1) prefId else liveId
    return profiles.map { profile ->
        val id = profile.id ?: 0
        ProfileListItem(
            id = id,
            name = profile.name.orEmpty(),
            host = profile.host.orEmpty(),
            active = id > 0 && id == activeId
        )
    }
}
