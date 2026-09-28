package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.profiles.ProfileEditScreen
import net.reichholf.dreamdroid.ui.text.asString

sealed interface TvProfilesPage {
    data object List : TvProfilesPage
    data class Edit(val profileId: Int) : TvProfilesPage
    data object Add : TvProfilesPage
}

sealed interface TvProfilesEvent {
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
    viewModel: TvProfilesHostViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnMarkReload by rememberUpdatedState(onMarkReload)
    val currentOnLeave by rememberUpdatedState(onLeave)

    LaunchedEffect(uiState.event) {
        val event = uiState.event ?: return@LaunchedEffect
        viewModel.onEventHandled()
        val policy = tvProfilesResultPolicy(event)
        if (policy.setResultOk) {
            currentOnMarkReload()
        }
        if (policy.finish) {
            currentOnLeave()
        }
    }

    BackHandler(enabled = uiState.page !is TvProfilesPage.List) {
        viewModel.showList()
    }

    val form = uiState.form
    if (uiState.page == TvProfilesPage.List || form == null) {
        TvProfilesScreen(
            profiles = uiState.profiles,
            onAdd = viewModel::showAdd,
            onActivate = viewModel::activate,
            onEdit = viewModel::showEdit,
            onDelete = {},
            onDeleteConfirmed = viewModel::delete,
            modifier = modifier
        )
    } else {
        ProfileEditScreen(
            form = form,
            hostError = uiState.hostError?.asString(),
            onFormChange = viewModel::onFormChange,
            saveLabel = stringResource(R.string.save),
            onSave = viewModel::save,
            modifier = modifier
        )
    }
}
