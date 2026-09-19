package net.reichholf.dreamdroid.tv.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.profiles.ProfileEditScreen
import net.reichholf.dreamdroid.ui.profiles.ProfileEditState
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.profiles.deleteConfirmedProfile
import net.reichholf.dreamdroid.ui.profiles.persistEditedProfile

internal sealed interface TvProfilesPage {
    data object List : TvProfilesPage
    data class Edit(val profileId: Int) : TvProfilesPage
    data object Add : TvProfilesPage
}

internal sealed interface TvProfilesEvent {
    data object ListBack : TvProfilesEvent
    data class Activate(val success: Boolean) : TvProfilesEvent
    data class Save(val saved: Boolean, val currentProfile: Boolean) : TvProfilesEvent
    data class Delete(val currentProfile: Boolean) : TvProfilesEvent
}

internal data class TvProfilesResultPolicy(val setResultOk: Boolean, val finish: Boolean)

/**
 * Result / finish table for TV profile mutations.
 *
 * [setResultOk] true means [Activity.setResult] `RESULT_OK`. False means leave the
 * current result as-is (never reset an already-set `RESULT_OK`; list Back relies on
 * the default `RESULT_CANCELED`).
 */
internal fun tvProfilesResultPolicy(event: TvProfilesEvent): TvProfilesResultPolicy = when (event) {
    TvProfilesEvent.ListBack ->
        TvProfilesResultPolicy(setResultOk = false, finish = true)

    is TvProfilesEvent.Activate ->
        TvProfilesResultPolicy(setResultOk = event.success, finish = event.success)

    is TvProfilesEvent.Save -> when {
        !event.saved -> TvProfilesResultPolicy(setResultOk = false, finish = false)
        event.currentProfile -> TvProfilesResultPolicy(setResultOk = true, finish = false)
        else -> TvProfilesResultPolicy(setResultOk = false, finish = false)
    }

    is TvProfilesEvent.Delete ->
        TvProfilesResultPolicy(
            setResultOk = event.currentProfile,
            finish = event.currentProfile
        )
}

internal fun tvProfileListItems(context: Context): List<ProfileListItem> {
    val dao = AppDatabase.profilesBlocking(context)
    val prefId = PreferenceManager.getDefaultSharedPreferences(context)
        .getInt(DreamDroid.CURRENT_PROFILE, -1)
    val liveId = DreamDroid.getCurrentProfile().id ?: -1
    val activeId = if (prefId > -1) prefId else liveId
    return dao.getProfiles().map { profile ->
        val id = profile.id ?: 0
        ProfileListItem(
            id = id,
            name = profile.name.orEmpty(),
            host = profile.host.orEmpty(),
            active = id > 0 && id == activeId
        )
    }
}

/**
 * TV Settings → Profile: list / add / edit / delete Room profiles.
 * Persist only on Save. List Back uses the system finish (default CANCELED).
 */
@Composable
fun TvProfilesHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as Activity
    var page by remember { mutableStateOf<TvProfilesPage>(TvProfilesPage.List) }
    var profiles by remember { mutableStateOf(tvProfileListItems(context)) }

    fun reloadRows() {
        profiles = tvProfileListItems(context)
    }

    fun applyPolicy(event: TvProfilesEvent) {
        val policy = tvProfilesResultPolicy(event)
        if (policy.setResultOk) {
            activity.setResult(Activity.RESULT_OK)
        }
        if (policy.finish) {
            activity.finish()
        }
    }

    fun onEditorOutcome(event: TvProfilesEvent.Save) {
        applyPolicy(event)
        if (event.saved) {
            page = TvProfilesPage.List
            reloadRows()
        }
    }

    BackHandler(enabled = page !is TvProfilesPage.List) {
        page = TvProfilesPage.List
        reloadRows()
    }

    when (val current = page) {
        TvProfilesPage.List -> {
            TvProfilesScreen(
                profiles = profiles,
                onAdd = { page = TvProfilesPage.Add },
                onActivate = { id ->
                    val success = DreamDroid.setCurrentProfile(activity, id, true)
                    applyPolicy(TvProfilesEvent.Activate(success))
                },
                onEdit = { id -> page = TvProfilesPage.Edit(id) },
                onDelete = {},
                onDeleteConfirmed = { id ->
                    val profile = AppDatabase.profilesBlocking(context).getProfile(id)
                        ?: return@TvProfilesScreen
                    val currentId = DreamDroid.getCurrentProfile().id
                    val deletingCurrent = profile.id != null && profile.id == currentId
                    deleteConfirmedProfile(context, profile)
                    applyPolicy(TvProfilesEvent.Delete(deletingCurrent))
                    if (!deletingCurrent) {
                        reloadRows()
                    }
                },
                modifier = modifier
            )
        }

        is TvProfilesPage.Edit -> {
            val loaded = remember(current.profileId) {
                AppDatabase.profilesBlocking(context).getProfile(current.profileId)
            }
            if (loaded == null) {
                LaunchedEffect(current.profileId) {
                    page = TvProfilesPage.List
                    reloadRows()
                }
            } else {
                TvProfilesEditor(
                    activity = activity,
                    profile = loaded,
                    onOutcome = { onEditorOutcome(it) },
                    modifier = modifier
                )
            }
        }

        TvProfilesPage.Add -> {
            val created = remember { Profile.getDefault() }
            TvProfilesEditor(
                activity = activity,
                profile = created,
                onOutcome = { onEditorOutcome(it) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun TvProfilesEditor(
    activity: Activity,
    profile: Profile,
    onOutcome: (TvProfilesEvent.Save) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(profile) { ProfileEditState.fromProfile(profile) }
    ProfileEditScreen(
        state = state,
        saveLabel = stringResource(R.string.save),
        onSave = {
            state.applyTo(profile)
            val outcome = persistEditedProfile(activity, profile)
            if (!outcome.saved) {
                state.hostError = activity.getString(R.string.host_empty)
                onOutcome(TvProfilesEvent.Save(saved = false, currentProfile = false))
            } else {
                val currentId = DreamDroid.getCurrentProfile().id
                val isCurrent = profile.id != null && profile.id == currentId
                if (isCurrent) {
                    DreamDroid.reloadCurrentProfile(activity)
                }
                onOutcome(TvProfilesEvent.Save(saved = true, currentProfile = isCurrent))
            }
        },
        modifier = modifier
    )
}
