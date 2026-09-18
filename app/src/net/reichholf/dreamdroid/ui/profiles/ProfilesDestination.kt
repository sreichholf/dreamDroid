package net.reichholf.dreamdroid.ui.profiles

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
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
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressDialog
import net.reichholf.dreamdroid.ui.nav.BindShellFab
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchDetectDevicesLoad

/**
 * Phase 2.7e: Profiles list as a direct Compose NavHost destination.
 * Reloads whenever this route enters composition (covers return from profile edit).
 */
@Composable
fun ProfilesDestination(handle: PhoneNavHandle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val listState = remember { ProfilesListState() }
    val session = remember { ProfilesSession() }
    session.handle = handle
    session.context = context
    session.activity = activity
    session.listState = listState

    DisposableEffect(handle, session) {
        activity.addMenuProvider(session)
        activity.title = context.getString(R.string.profiles)
        onDispose {
            activity.removeMenuProvider(session)
            session.finishActionMode()
            session.cancelDetect()
        }
    }

    val addLabel = stringResource(R.string.profile_add)
    BindShellFab(
        contentDescription = addLabel,
        iconRes = R.drawable.ic_action_fab_add,
        onClick = { session.createProfile() },
        text = addLabel
    )

    LaunchedEffect(Unit) {
        session.reloadProfiles()
    }

    var showDetectProgress by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var discoveredDevices by remember { mutableStateOf<List<Profile>?>(null) }
    var discoveryFailed by remember { mutableStateOf(false) }
    session.onRequestDeleteConfirm = { title -> showDeleteConfirm = title }
    session.onDetectProgressChanged = { showDetectProgress = it }
    session.onDiscoveryResult = { found ->
        if (found.isEmpty()) {
            discoveredDevices = null
            discoveryFailed = true
        } else {
            discoveryFailed = false
            discoveredDevices = found
        }
    }

    ProfilesScreen(
        profiles = listState.items,
        onProfileClick = { item -> session.onProfileRowClick(item) },
        onProfileLongClick = { item -> session.onProfileRowLongClick(item) },
        modifier = modifier
    )

    if (showDetectProgress) {
        IndeterminateProgressDialog(
            title = stringResource(R.string.searching),
            message = stringResource(R.string.searching_known_devices),
            onDismiss = { showDetectProgress = false }
        )
    }
    showDeleteConfirm?.let { title ->
        ConfirmAlertDialog(
            title = title,
            message = stringResource(R.string.confirm_delete_profile),
            onDismiss = { showDeleteConfirm = null },
            onConfirm = {
                session.deleteProfileConfirmed()
                showDeleteConfirm = null
            },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }
    discoveredDevices?.let { found ->
        AutodiscoveryDevicesDialog(
            devices = found,
            onDismiss = { discoveredDevices = null },
            onPick = { index ->
                session.editDetectedProfile(index)
                discoveredDevices = null
            },
            onReload = {
                discoveredDevices = null
                session.reloadDetectedDevices()
            },
            onAddAll = {
                session.addAllDetected()
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

private class ProfilesSession : MenuProvider {
    var handle: PhoneNavHandle? = null
    var onRequestDeleteConfirm: ((String) -> Unit)? = null
    var onDiscoveryResult: ((ArrayList<Profile>) -> Unit)? = null
    var context: android.content.Context? = null
    var activity: AppCompatActivity? = null
    var listState: ProfilesListState? = null
    var onDetectProgressChanged: ((Boolean) -> Unit)? = null

    private val profiles = ArrayList<Profile>()
    private var selected: Profile = Profile.getDefault()
    private var detectedProfiles: ArrayList<Profile>? = null
    private var detectJob: kotlinx.coroutines.Job? = null
    private var actionMode: ActionMode? = null
    private var actionModeActive = false
    private var actionModeRequired = false

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.profilelist_context, menu)
            actionModeActive = true
            actionModeRequired = false
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = true

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            mode.finish()
            return onItemClicked(item.itemId)
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionModeActive = false
            actionMode = null
        }
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun finishActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    fun cancelDetect() {
        detectJob?.cancel()
        detectJob = null
        onDetectProgressChanged?.invoke(false)
    }

    fun reloadProfiles() {
        val ctx = context ?: return
        val state = listState ?: return
        val dao = AppDatabase.profilesBlocking(ctx)
        profiles.clear()
        profiles.addAll(dao.getProfiles())
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val activeProfileId = sp.getInt(DreamDroid.CURRENT_PROFILE, -1)
        val rows = profiles.map { m ->
            val isActive = activeProfileId > -1 && m.id != null && activeProfileId == m.id
            ProfileListItem(m.id ?: 0, m.name.orEmpty(), m.host.orEmpty(), isActive)
        }
        state.replaceAll(rows)
    }

    fun onProfileRowClick(item: ProfileListItem) {
        selectProfile(item)
        if (actionModeActive) {
            return
        }
        activateProfile()
    }

    fun onProfileRowLongClick(item: ProfileListItem) {
        selectProfile(item)
        val act = activity ?: return
        actionMode = act.startSupportActionMode(actionModeCallback)
    }

    private fun selectProfile(item: ProfileListItem) {
        for (p in profiles) {
            if (p.id != null && p.id == item.id) {
                selected = p
                return
            }
        }
    }

    private fun activateProfile() {
        val act = activity ?: return
        if (DreamDroid.setCurrentProfile(act, selected.id ?: -1, true)) {
            toast(act.getText(R.string.profile_activated).toString() + " '" + selected.name + "'")
        } else {
            toast(
                act.getText(R.string.profile_not_activated).toString() + " '" + selected.name + "'"
            )
        }
        reloadProfiles()
    }

    fun createProfile() {
        ProfilesNavigation.openProfileEdit(activity ?: return, null)
    }

    private fun editProfile() {
        ProfilesNavigation.openProfileEdit(activity ?: return, selected)
    }

    private fun detectDevices() {
        val host = handle ?: return
        if (activity == null) {
            return
        }
        val cached = detectedProfiles
        if (cached == null) {
            cancelDetect()
            onDetectProgressChanged?.invoke(true)
            detectJob = host.launchDetectDevicesLoad { profiles ->
                detectJob = null
                onDevicesDetected(profiles)
            }
        } else if (cached.isEmpty()) {
            detectedProfiles = null
            detectDevices()
        } else {
            onDevicesDetected(cached)
        }
    }

    private fun addAllDetectedDevices() {
        val ctx = context ?: return
        val detected = detectedProfiles ?: return
        val dao = AppDatabase.profilesBlocking(ctx)
        for (p in detected) {
            p.id = dao.addProfile(p).toInt()
            toast(ctx.getText(R.string.profile_added).toString() + " '" + p.name + "'")
        }
        reloadProfiles()
    }

    fun reloadDetectedDevices() {
        detectedProfiles = null
        detectDevices()
    }

    fun addAllDetected() {
        addAllDetectedDevices()
    }

    fun editDetectedProfile(index: Int) {
        val found = detectedProfiles ?: return
        if (index in found.indices) {
            selected = found[index]
            editProfile()
        }
    }

    private fun onDevicesDetected(found: ArrayList<Profile>) {
        onDetectProgressChanged?.invoke(false)
        detectedProfiles = found
        onDiscoveryResult?.invoke(found)
    }

    private fun onItemClicked(id: Int): Boolean = when (id) {
        Statics.ITEM_ADD_PROFILE -> {
            createProfile()
            true
        }

        Statics.ITEM_DETECT_DEVICES -> {
            detectDevices()
            true
        }

        Statics.ITEM_EDIT -> {
            editProfile()
            true
        }

        Statics.ITEM_DELETE -> {
            onRequestDeleteConfirm?.invoke(selected.name.orEmpty())
            true
        }

        else -> false
    }

    fun deleteProfileConfirmed() {
        val ctx = context ?: return
        toast(deleteConfirmedProfile(ctx, selected))
        reloadProfiles()
        selected = Profile.getDefault()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.profiles, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = onItemClicked(menuItem.itemId)
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
