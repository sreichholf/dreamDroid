package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ProfileEdit
import net.reichholf.dreamdroid.ui.nav.ShellMessages

/**
 * Profile create/edit as a Compose NavHost destination.
 * The working profile and [ProfileEditState] live on [ProfileEditViewModel].
 * Remounts when [route] or [remountEpoch] changes.
 */
@Composable
fun ProfileEditDestination(
    handle: PhoneNavHandle,
    route: ProfileEdit,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val tag = route.tag()
    LaunchedEffect(tag, remountEpoch, route) {
        viewModel.start(route, remountEpoch)
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (!viewModel.ready) {
        return
    }
    val canDelete = viewModel.canDelete

    fun toast(message: CharSequence) {
        ShellMessages.post(message)
    }

    fun save() {
        val outcome = viewModel.save()
        if (outcome.saved) {
            toast(outcome.message)
            handle.deliverPickResult(Activity.RESULT_OK, null)
        }
    }

    fun delete() {
        toast(viewModel.delete())
        handle.deliverPickResult(Activity.RESULT_OK, null)
    }

    BindShellTopBarActions(
        saveAndDeleteActions(
            saveLabel = stringResource(R.string.save),
            deleteLabel = stringResource(R.string.delete),
            canDelete = canDelete,
            onSave = { save() },
            onDelete = { showDeleteConfirm = true }
        )
    )

    val title = stringResource(R.string.edit_profile)
    DisposableEffect(handle, tag, remountEpoch, viewModel) {
        val activity = context as? AppCompatActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.persist()
            }
        }
        activity?.title = title
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            viewModel.persistIfBound(tag, remountEpoch)
        }
    }

    LaunchedEffect(tag, remountEpoch) {
        (context as? AppCompatActivity)?.title = title
    }

    ProfileEditScreen(
        state = viewModel.editState,
        saveLabel = stringResource(R.string.save),
        onSave = { save() },
        showSaveFab = false,
        modifier = modifier
    )

    if (showDeleteConfirm) {
        ConfirmAlertDialog(
            title = viewModel.profile.name.orEmpty(),
            message = stringResource(R.string.confirm_delete_profile),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { delete() },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
}

internal data class ProfilePersistOutcome(val saved: Boolean, val message: String)

internal fun persistEditedProfile(context: Context, profile: Profile): ProfilePersistOutcome {
    if (profile.host.isNullOrEmpty()) {
        return ProfilePersistOutcome(
            saved = false,
            message = context.getString(R.string.host_empty)
        )
    }
    if (profile.streamHost == null) {
        profile.streamHost = ""
    }
    val dao = AppDatabase.profilesBlocking(context)
    val id = profile.id ?: 0
    if (id > 0) {
        dao.updateProfile(profile)
        if (profile.id == ProfileRepository.get().requireCurrent().id) {
            ProfileRepository.get().setCurrent(profile)
        }
        return ProfilePersistOutcome(
            saved = true,
            message = context.getText(R.string.profile_updated).toString() +
                " '" + profile.name + "'"
        )
    }
    profile.id = dao.addProfile(profile).toInt()
    return ProfilePersistOutcome(
        saved = true,
        message = context.getText(R.string.profile_added).toString() +
            " '" + profile.name + "'"
    )
}
