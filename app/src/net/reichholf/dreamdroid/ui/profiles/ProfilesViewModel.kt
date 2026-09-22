package net.reichholf.dreamdroid.ui.profiles

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Owns one [ProfilesListState], profile activation, and receiver discovery.
 * Jobs stay on [viewModelScope] so leaving the destination does not cancel them.
 *
 * [savedStateHandle] lets viewModel() construct this class. This screen has no
 * rememberSaveable fields, so no keys are written.
 */
class ProfilesViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val listState: ProfilesListState = ProfilesListState()

    var detectInProgress by mutableStateOf(false)
        private set

    private val discoveryChannel = Channel<List<Profile>>(Channel.CONFLATED)
    val discoveryResults: Flow<List<Profile>> = discoveryChannel.receiveAsFlow()

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = toastChannel.receiveAsFlow()

    private val profiles = ArrayList<Profile>()
    private var selected: Profile = Profile.getDefault()
    private var detectedProfiles: ArrayList<Profile>? = null
    private var reloadJob: Job? = null
    private var detectJob: Job? = null
    private var addJob: Job? = null

    fun reloadProfiles() {
        val app = getApplication<Application>()
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val loaded = AppDatabase.profilesBlocking(app).getProfiles()
            if (!isActive) {
                return@launch
            }
            publishProfiles(app, loaded)
        }
    }

    fun activateProfile(item: ProfileListItem) {
        selectProfile(item)
        val app = getApplication<Application>()
        val activated = DreamDroid.setCurrentProfile(app, selected.id ?: -1, true)
        val label = if (activated) {
            R.string.profile_activated
        } else {
            R.string.profile_not_activated
        }
        postToast(label, selected.name)
        reloadProfiles()
    }

    fun profileToEdit(item: ProfileListItem): Profile {
        selectProfile(item)
        return selected
    }

    fun detectDevices() {
        if (detectJob?.isActive == true) {
            return
        }
        val cached = detectedProfiles
        if (cached == null) {
            detectInProgress = true
            detectJob = viewModelScope.launch {
                try {
                    val found = withContext(Dispatchers.IO) {
                        DeviceDetector.getAvailableHosts()
                    }
                    if (isActive) {
                        onDevicesDetected(found)
                    }
                } finally {
                    detectJob = null
                    detectInProgress = false
                }
            }
        } else if (cached.isEmpty()) {
            detectedProfiles = null
            detectDevices()
        } else {
            onDevicesDetected(cached)
        }
    }

    fun reloadDetectedDevices() {
        detectedProfiles = null
        detectDevices()
    }

    fun addAllDetected() {
        val detected = detectedProfiles ?: return
        val app = getApplication<Application>()
        addJob?.cancel()
        addJob = viewModelScope.launch {
            val dao = AppDatabase.profilesBlocking(app)
            for (profile in detected) {
                profile.id = dao.addProfile(profile).toInt()
                postToast(R.string.profile_added, profile.name)
            }
            if (isActive) {
                reloadProfiles()
            }
        }
    }

    fun detectedProfileToEdit(index: Int): Profile? {
        val found = detectedProfiles ?: return null
        if (index !in found.indices) {
            return null
        }
        selected = found[index]
        return selected
    }

    private fun onDevicesDetected(found: ArrayList<Profile>) {
        detectInProgress = false
        detectedProfiles = found
        discoveryChannel.trySend(found)
    }

    private fun publishProfiles(app: Application, loaded: List<Profile>) {
        val activeProfileId = PreferenceManager.getDefaultSharedPreferences(app)
            .getInt(DreamDroid.CURRENT_PROFILE, -1)
        profiles.clear()
        profiles.addAll(loaded)
        val rows = profiles.map { profile ->
            val active = activeProfileId > -1 &&
                profile.id != null &&
                activeProfileId == profile.id
            ProfileListItem(
                id = profile.id ?: 0,
                name = profile.name.orEmpty(),
                host = profile.host.orEmpty(),
                active = active
            )
        }
        listState.replaceAll(rows)
    }

    private fun selectProfile(item: ProfileListItem) {
        for (profile in profiles) {
            if (profile.id != null && profile.id == item.id) {
                selected = profile
                return
            }
        }
    }

    private fun postToast(messageRes: Int, name: String?) {
        val app = getApplication<Application>()
        toastChannel.trySend(app.getText(messageRes).toString() + " '" + name + "'")
    }
}
