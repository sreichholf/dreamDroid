package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.ui.profiles.ProfileEditScreen
import net.reichholf.dreamdroid.ui.profiles.ProfileEditState
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
 * Reload / leave table for TV profile mutations.
 *
 * [TvProfilesResultPolicy.setResultOk] marks the hub to reload when this destination
 * pops. False leaves a mark already set by an earlier save. List Back does not clear it.
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

/**
 * TV Settings → Profile: list / add / edit / delete Room profiles.
 * [TvProfilesHostViewModel] owns the page and the open editor. Persist only on Save.
 * List Back pops this destination and does not mark a reload by itself.
 */
@Composable
fun TvProfilesHost(
    modifier: Modifier = Modifier,
    onMarkReload: () -> Unit = {},
    onLeave: () -> Unit = {},
    viewModel: TvProfilesHostViewModel = viewModel()
) {
    val context = LocalContext.current
    val page = viewModel.page

    fun applyPolicy(event: TvProfilesEvent) {
        val policy = tvProfilesResultPolicy(event)
        if (policy.setResultOk) {
            onMarkReload()
        }
        if (policy.finish) {
            onLeave()
        }
    }

    fun onEditorOutcome(event: TvProfilesEvent.Save) {
        applyPolicy(event)
        if (event.saved) {
            viewModel.showList()
        }
    }

    fun confirmDelete(id: Int) {
        val profile = viewModel.loadedProfile(id) ?: return
        val currentId = ProfileRepository.get().current.value?.id
        val deletingCurrent = profile.id != null && profile.id == currentId
        deleteConfirmedProfile(context, profile)
        applyPolicy(TvProfilesEvent.Delete(deletingCurrent))
        if (!deletingCurrent) {
            viewModel.reload()
        }
    }

    BackHandler(enabled = page !is TvProfilesPage.List) {
        viewModel.showList()
    }

    when (val current = page) {
        TvProfilesPage.List -> {
            TvProfilesScreen(
                profiles = viewModel.profiles,
                onAdd = { viewModel.showAdd() },
                onActivate = { id ->
                    val success = ProfileRepository.get().setCurrent(context, id, true)
                    applyPolicy(TvProfilesEvent.Activate(success))
                },
                onEdit = { id -> viewModel.showEdit(id) },
                onDelete = {},
                onDeleteConfirmed = { id -> confirmDelete(id) },
                modifier = modifier
            )
        }

        is TvProfilesPage.Edit -> {
            val profile = viewModel.editingProfile
            val state = viewModel.editState
            if (profile != null && state != null && profile.id == current.profileId) {
                TvProfilesEditor(
                    profile = profile,
                    state = state,
                    onOutcome = { onEditorOutcome(it) },
                    modifier = modifier
                )
            }
        }

        TvProfilesPage.Add -> {
            val profile = viewModel.editingProfile
            val state = viewModel.editState
            if (profile != null && state != null) {
                TvProfilesEditor(
                    profile = profile,
                    state = state,
                    onOutcome = { onEditorOutcome(it) },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun TvProfilesEditor(
    profile: Profile,
    state: ProfileEditState,
    onOutcome: (TvProfilesEvent.Save) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostEmpty = stringResource(R.string.host_empty)
    ProfileEditScreen(
        state = state,
        saveLabel = stringResource(R.string.save),
        onSave = {
            state.applyTo(profile)
            val outcome = persistEditedProfile(context, profile)
            if (!outcome.saved) {
                state.hostError = hostEmpty
                onOutcome(TvProfilesEvent.Save(saved = false, currentProfile = false))
            } else {
                val currentId = ProfileRepository.get().current.value?.id
                val isCurrent = profile.id != null && profile.id == currentId
                if (isCurrent) {
                    ProfileRepository.get().reloadCurrent(context)
                }
                onOutcome(TvProfilesEvent.Save(saved = true, currentProfile = isCurrent))
            }
        },
        modifier = modifier
    )
}
