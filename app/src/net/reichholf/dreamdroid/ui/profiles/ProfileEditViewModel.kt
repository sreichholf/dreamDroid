package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.ui.nav.ProfileEdit
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * One profile being created or edited. [form] is null while a saved profile loads.
 * [finished] is set once a save or delete went through; the destination then leaves
 * with [finished]'s message.
 */
data class ProfileEditUiState(
    val form: ProfileForm? = null,
    val hostError: UiText? = null,
    val savedName: String = "",
    val canDelete: Boolean = false,
    val finished: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.edit_profile)
}

/**
 * Create or edit the profile named by the [ProfileEdit] route. The typed fields and
 * the profile they apply to survive process death in the [SavedStateHandle].
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private var profile: Profile = Profile.getDefault()

    init {
        val savedProfile = savedStateHandle.get<Profile>(KEY_PROFILE)
        val savedForm = savedStateHandle.get<ProfileForm>(KEY_FORM)
        val route = savedStateHandle.profileEditRoute()
        when {
            savedProfile != null && savedForm != null -> bind(savedProfile, savedForm)

            route.profileId > 0 -> viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { profiles.profile(route.profileId) }
                val next = loaded ?: Profile.getDefault()
                bind(next, ProfileForm.from(next))
            }

            else -> {
                val next = route.launchProfile() ?: Profile.getDefault()
                bind(next, ProfileForm.from(next))
            }
        }
    }

    fun onFormChange(form: ProfileForm) {
        savedStateHandle[KEY_FORM] = form
        _uiState.update {
            val hostError = if (form.host == it.form?.host) it.hostError else null
            it.copy(form = form, hostError = hostError)
        }
    }

    fun save() {
        val form = _uiState.value.form ?: return
        if (form.host.isBlank()) {
            _uiState.update { it.copy(hostError = UiText.Resource(R.string.host_empty)) }
            return
        }
        form.applyTo(profile)
        if (profile.streamHost == null) {
            profile.streamHost = ""
        }
        val edited = profile
        val message = if ((edited.id ?: 0) > 0) R.string.profile_updated else R.string.profile_added
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profiles.save(edited) }
            _uiState.update { it.copy(finished = namedMessage(message, edited.name)) }
        }
    }

    fun delete() {
        val deleted = profile
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profiles.delete(deleted) }
            _uiState.update {
                it.copy(finished = namedMessage(R.string.profile_deleted, deleted.name))
            }
        }
    }

    private fun bind(next: Profile, form: ProfileForm) {
        profile = next
        savedStateHandle[KEY_PROFILE] = next
        savedStateHandle[KEY_FORM] = form
        _uiState.value = ProfileEditUiState(
            form = form,
            savedName = next.name.orEmpty(),
            canDelete = (next.id ?: 0) > 0
        )
    }

    private companion object {
        const val KEY_PROFILE = "profile_edit_profile"
        const val KEY_FORM = "profile_edit_form"
    }
}

/**
 * The [ProfileEdit] arguments, read by name. `toRoute()` decodes through `Bundle`, which
 * JVM tests only have as a stub.
 */
private fun SavedStateHandle.profileEditRoute(): ProfileEdit {
    val defaults = ProfileEdit()
    return ProfileEdit(
        profileId = get<Int>("profileId") ?: defaults.profileId,
        name = get<String>("name") ?: defaults.name,
        host = get<String>("host") ?: defaults.host,
        streamHost = get<String>("streamHost") ?: defaults.streamHost,
        port = get<Int>("port") ?: defaults.port,
        user = get<String>("user") ?: defaults.user,
        simpleRemote = get<Boolean>("simpleRemote") ?: defaults.simpleRemote
    )
}

/** "Added profile 'Living Room'" and friends. */
internal fun namedMessage(label: Int, name: String?): UiText =
    UiText.Resource(R.string.named_message, listOf(UiText.Resource(label), name.orEmpty()))
