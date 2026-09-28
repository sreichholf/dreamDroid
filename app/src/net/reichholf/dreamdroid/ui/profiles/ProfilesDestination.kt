package net.reichholf.dreamdroid.ui.profiles

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/**
 * Profiles list as a NavHost destination. The list reloads each time this route enters
 * composition, which covers the return from the editor.
 */
@Composable
fun ProfilesDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = Statics.ITEM_DETECT_DEVICES,
                label = stringResource(R.string.autodiscover_dreamboxes),
                iconRes = R.drawable.ic_action_devices,
                enabled = !uiState.detecting,
                onClick = viewModel::detectDevices
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
        viewModel.refresh()
    }

    ProfilesScreen(
        profiles = uiState.profiles,
        onProfileClick = viewModel::activate,
        onProfileEdit = { item ->
            viewModel.profileFor(item)?.let { handle.navigateToProfileEdit(it) }
        },
        modifier = modifier
    )

    if (uiState.detecting) {
        IndeterminateProgressHost(
            IndeterminateProgressState(
                title = stringResource(R.string.searching),
                message = stringResource(R.string.searching_known_devices)
            )
        )
    }
    uiState.discovered?.let { found ->
        AutodiscoveryDevicesDialog(
            devices = found,
            onDismiss = viewModel::dismissDiscovery,
            onPick = { index ->
                viewModel.dismissDiscovery()
                handle.navigateToProfileEdit(found[index])
            },
            onReload = viewModel::reloadDetectedDevices,
            onAddAll = viewModel::addAllDetected
        )
    }
    if (uiState.discoveryFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscovery,
            title = { Text(stringResource(R.string.autodiscover_dreamboxes)) },
            text = { Text(stringResource(R.string.autodiscovery_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDiscovery) {
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
