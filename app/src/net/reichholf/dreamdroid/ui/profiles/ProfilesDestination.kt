package net.reichholf.dreamdroid.ui.profiles

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellMessages
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction

/**
 * Phase 2.7e: Profiles list as a direct Compose NavHost destination.
 * The list, activation, and receiver discovery live on [ProfilesViewModel].
 * Reloads whenever this route enters composition (covers return from profile edit).
 */
@Composable
fun ProfilesDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val detectInProgress = viewModel.detectInProgress

    val title = stringResource(R.string.profiles)
    LaunchedEffect(activity, title) {
        activity.title = title
    }
    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = Statics.ITEM_DETECT_DEVICES,
                label = stringResource(R.string.autodiscover_dreamboxes),
                iconRes = R.drawable.ic_action_devices,
                enabled = !detectInProgress,
                onClick = { viewModel.detectDevices() }
            )
        )
    )

    val addLabel = stringResource(R.string.profile_add)
    BindShellFab(
        contentDescription = addLabel,
        iconRes = R.drawable.ic_action_fab_add,
        onClick = { handle.navigateToProfileEdit(null) },
        text = addLabel
    )

    LaunchedEffect(viewModel) {
        viewModel.reloadProfiles()
    }

    var showDetectProgress by remember { mutableStateOf(detectInProgress) }
    var discoveredDevices by remember { mutableStateOf<List<Profile>?>(null) }
    var discoveryFailed by remember { mutableStateOf(false) }
    LaunchedEffect(detectInProgress) {
        showDetectProgress = detectInProgress
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            ShellMessages.post(message)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.discoveryResults.collect { found ->
            if (found.isEmpty()) {
                discoveredDevices = null
                discoveryFailed = true
            } else {
                discoveryFailed = false
                discoveredDevices = found
            }
        }
    }

    ProfilesScreen(
        profiles = viewModel.listState.items,
        onProfileClick = { item -> viewModel.activateProfile(item) },
        onProfileEdit = { item ->
            handle.navigateToProfileEdit(viewModel.profileToEdit(item))
        },
        modifier = modifier
    )

    if (showDetectProgress) {
        IndeterminateProgressHost(
            IndeterminateProgressState(
                title = stringResource(R.string.searching),
                message = stringResource(R.string.searching_known_devices)
            )
        )
    }
    discoveredDevices?.let { found ->
        AutodiscoveryDevicesDialog(
            devices = found,
            onDismiss = { discoveredDevices = null },
            onPick = { index ->
                viewModel.detectedProfileToEdit(index)?.let { profile ->
                    handle.navigateToProfileEdit(profile)
                }
                discoveredDevices = null
            },
            onReload = {
                discoveredDevices = null
                viewModel.reloadDetectedDevices()
            },
            onAddAll = {
                viewModel.addAllDetected()
                discoveredDevices = null
            }
        )
    }
    if (discoveryFailed) {
        AlertDialog(
            onDismissRequest = { discoveryFailed = false },
            title = { Text(stringResource(R.string.autodiscover_dreamboxes)) },
            text = { Text(stringResource(R.string.autodiscovery_failed)) },
            confirmButton = {
                TextButton(onClick = { discoveryFailed = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun AutodiscoveryDevicesDialog(
    devices: List<Profile>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onReload: () -> Unit,
    onAddAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autodiscover_dreamboxes)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                devices.forEachIndexed { index, profile ->
                    val label = String.format("%s (%s)", profile.name, profile.host)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = false,
                                role = Role.RadioButton,
                                onClick = { onPick(index) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReload) {
                Text(stringResource(R.string.reload))
            }
        },
        dismissButton = {
            TextButton(onClick = onAddAll) {
                Text(stringResource(R.string.add_all))
            }
        }
    )
}

internal fun deleteConfirmedProfile(context: Context, profile: Profile): String {
    val deletedId = profile.id
    val currentId = ProfileRepository.get().requireCurrent().id
    AppDatabase.profilesBlocking(context).deleteProfile(profile)
    if (deletedId != null) {
        runBlocking(Dispatchers.IO) {
            UseDrivenCache.clearForProfile(AppDatabase.database(context), deletedId)
        }
    }
    if (deletedId != null && deletedId == currentId) {
        val next = AppDatabase.profilesBlocking(context).getProfiles()
            .firstOrNull { it.id != null && it.id != deletedId }
        if (next != null) {
            ProfileRepository.get().setCurrent(context, next.id!!, true)
        } else {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(DreamDroid.CURRENT_PROFILE)
                .apply()
            ProfileRepository.get().setCurrent(Profile.getDefault())
        }
    }
    return context.getString(R.string.profile_deleted) + " '" + profile.name + "'"
}
