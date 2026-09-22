package net.reichholf.dreamdroid.ui.profiles

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.PhoneNavSavedKeys

internal enum class ProfileEditBind {
    Keep,
    RestoreSaved,
    LoadLaunch
}

/**
 * First bind restores a saved snapshot when its tag matches the route.
 * A later tag or remount epoch loads the launch profile instead.
 * Epoch mismatch on the first bind still restores: the host epoch restarts at 0.
 */
internal fun profileEditBind(
    hasBound: Boolean,
    boundTag: String,
    boundEpoch: Int,
    routeTag: String,
    remountEpoch: Int,
    savedTag: String?
): ProfileEditBind {
    if (hasBound && boundTag == routeTag && boundEpoch == remountEpoch) {
        return ProfileEditBind.Keep
    }
    if (!hasBound && savedTag == routeTag) {
        return ProfileEditBind.RestoreSaved
    }
    return ProfileEditBind.LoadLaunch
}

/** Same choice the destination used to make from the launch bundle. */
internal fun initialProfileForEdit(action: String?, profile: Profile?): Profile {
    if (Intent.ACTION_EDIT == action && profile != null) {
        return profile
    }
    return Profile.getDefault()
}

/**
 * Working [Profile] and [ProfileEditState] for [ProfileEditDestination].
 * Delete confirmation stays in the composable.
 * The snapshot uses [PhoneNavSavedKeys.PROFILE_EDIT_ARGS] (action + profile)
 * and [PhoneNavSavedKeys.PROFILE_EDIT_TAG] so a new launch is not restored
 * over a different profile.
 */
class ProfileEditViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val editState: ProfileEditState = ProfileEditState()

    var profile by mutableStateOf(Profile.getDefault())
        private set

    var canDelete by mutableStateOf(false)
        private set

    var ready by mutableStateOf(false)
        private set

    private var hasBound: Boolean = false
    private var boundTag: String = ""
    private var boundEpoch: Int = 0
    private var action: String = Intent.ACTION_EDIT

    fun start(handle: PhoneNavHandle) {
        val tag = handle.profileEditRouteTag()
        val remount = handle.profileEditRemountEpoch
        val saved = if (hasBound) null else readSavedEdit()
        when (
            profileEditBind(
                hasBound = hasBound,
                boundTag = boundTag,
                boundEpoch = boundEpoch,
                routeTag = tag,
                remountEpoch = remount,
                savedTag = saved?.tag
            )
        ) {
            ProfileEditBind.Keep -> {
                ready = true
                return
            }

            ProfileEditBind.RestoreSaved -> {
                val restored = checkNotNull(saved)
                applyProfile(restored.profile, restored.action, tag, remount)
            }

            ProfileEditBind.LoadLaunch -> loadFromLaunch(handle, tag, remount)
        }
    }

    internal fun save(): ProfilePersistOutcome {
        val app = getApplication<Application>()
        editState.applyTo(profile)
        val outcome = persistEditedProfile(app, profile)
        if (!outcome.saved) {
            editState.hostError = app.getString(R.string.host_empty)
        } else {
            persist()
        }
        return outcome
    }

    fun delete(): String {
        val app = getApplication<Application>()
        return deleteConfirmedProfile(app, profile)
    }

    /** Writes the in-progress profile. Text fields are copied in first. */
    fun persist() {
        if (!hasBound) {
            return
        }
        editState.applyTo(profile)
        val bundle = Bundle().apply {
            putString(NavExtras.ACTION, action)
            putSerializable(NavExtras.DATA, profile)
        }
        savedStateHandle[PhoneNavSavedKeys.PROFILE_EDIT_ARGS] = bundle
        savedStateHandle[PhoneNavSavedKeys.PROFILE_EDIT_TAG] = boundTag
    }

    /**
     * Pause/dispose flush. Skips when [start] has already moved on to another
     * tag or epoch, so an older composition cannot overwrite the new snapshot.
     */
    fun persistIfBound(tag: String, epoch: Int) {
        if (hasBound && boundTag == tag && boundEpoch == epoch) {
            persist()
        }
    }

    private fun loadFromLaunch(handle: PhoneNavHandle, tag: String, remount: Int) {
        val args = handle.profileEditLeafArguments()
        val launchAction = args.getString(NavExtras.ACTION)
        val extras = args.getSerializableCompat<Profile>(NavExtras.DATA)
        applyProfile(
            initialProfileForEdit(launchAction, extras),
            launchAction ?: Intent.ACTION_EDIT,
            tag,
            remount
        )
    }

    private fun applyProfile(next: Profile, nextAction: String, tag: String, remount: Int) {
        action = nextAction
        profile = next
        canDelete = (next.id ?: 0) > 0
        editState.loadFrom(next)
        boundTag = tag
        boundEpoch = remount
        hasBound = true
        ready = true
        persist()
    }

    private fun readSavedEdit(): SavedProfileEdit? {
        val tag = savedStateHandle.get<String>(PhoneNavSavedKeys.PROFILE_EDIT_TAG) ?: return null
        val bundle = savedStateHandle.get<Bundle>(PhoneNavSavedKeys.PROFILE_EDIT_ARGS)
            ?: return null
        val savedProfile = bundle.getSerializableCompat<Profile>(NavExtras.DATA) ?: return null
        val savedAction = bundle.getString(NavExtras.ACTION) ?: Intent.ACTION_EDIT
        return SavedProfileEdit(action = savedAction, profile = savedProfile, tag = tag)
    }
}

private data class SavedProfileEdit(val action: String, val profile: Profile, val tag: String)
