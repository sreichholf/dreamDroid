package net.reichholf.dreamdroid.ui.profiles

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
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
import androidx.core.view.MenuProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

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
    DisposableEffect(handle, viewModel) {
        val menuProvider = ProfilesMenuProvider(
            viewModel = viewModel,
            onAddProfile = { handle.navigateToProfileEdit(null) }
        )
        activity.addMenuProvider(menuProvider)
        activity.title = title
        onDispose {
            activity.removeMenuProvider(menuProvider)
        }
    }

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
        activity.invalidateOptionsMenu()
    }
    LaunchedEffect(viewModel, context) {
        viewModel.toastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

private class ProfilesMenuProvider(
    private val viewModel: ProfilesViewModel,
    private val onAddProfile: () -> Unit
) : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.profiles, menu)
        applyDetectEnabled(menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        applyDetectEnabled(menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        Statics.ITEM_ADD_PROFILE -> {
            onAddProfile()
            true
        }

        Statics.ITEM_DETECT_DEVICES -> {
            viewModel.detectDevices()
            true
        }

        else -> false
    }

    private fun applyDetectEnabled(menu: Menu) {
        menu.findItem(Statics.ITEM_DETECT_DEVICES)?.isEnabled = !viewModel.detectInProgress
    }
}

internal fun deleteConfirmedProfile(context: Context, profile: Profile): String {
    val deletedId = profile.id
    val currentId = DreamDroid.getCurrentProfile().id
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
            DreamDroid.setCurrentProfile(context, next.id!!, true)
        } else {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(DreamDroid.CURRENT_PROFILE)
                .apply()
            DreamDroid.setCurrentProfile(Profile.getDefault())
        }
    }
    return context.getString(R.string.profile_deleted) + " '" + profile.name + "'"
}
