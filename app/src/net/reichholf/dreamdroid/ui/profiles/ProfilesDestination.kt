package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.launchDetectDevicesLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressDialog
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Phase 2.7e: Profiles list as a direct Compose NavHost destination.
 * Reloads whenever this route enters composition (covers return from profile edit).
 */
@Composable
fun ProfilesDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val listState = remember { ProfilesListState() }
    val session = remember { ProfilesSession() }
    session.hostFragment = hostFragment
    session.context = context
    session.activity = activity
    session.listState = listState

    DisposableEffect(hostFragment, session) {
        hostFragment.composeDialogActionListener = session
        activity.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        activity.title = context.getString(R.string.profiles)
        val fab = activity.findViewById<FloatingActionButton?>(R.id.fab_main)
        fab?.let {
            it.show()
            it.contentDescription = context.getString(R.string.profile_add)
            it.setImageResource(R.drawable.ic_action_fab_add)
            it.setOnClickListener { session.createProfile() }
            it.setOnLongClickListener { v ->
                Toast.makeText(activity, v.contentDescription, Toast.LENGTH_SHORT).show()
                true
            }
        }
        onDispose {
            if (hostFragment.composeDialogActionListener === session) {
                hostFragment.composeDialogActionListener = null
            }
            activity.removeMenuProvider(session)
            session.finishActionMode()
            session.cancelDetect()
            fab?.let {
                it.setOnClickListener(null)
                it.setOnLongClickListener(null)
                it.hide()
            }
            (activity as? MainActivity)?.unregisterFab(R.id.fab_main)
        }
    }

    LaunchedEffect(Unit) {
        session.reloadProfiles()
    }

    var showDetectProgress by remember { mutableStateOf(false) }
    session.onDetectProgressChanged = { showDetectProgress = it }

    ProfilesScreen(
        profiles = listState.items,
        onProfileClick = { item -> session.onProfileRowClick(item) },
        onProfileLongClick = { item -> session.onProfileRowLongClick(item) },
        modifier = modifier,
    )

    if (showDetectProgress) {
        IndeterminateProgressDialog(
            title = stringResource(R.string.searching),
            message = stringResource(R.string.searching_known_devices),
        )
    }
}

private class ProfilesSession :
    ActionDialog.DialogActionListener,
    MenuProvider {
    var hostFragment: PhoneNavHostFragment? = null
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
        val dao = AppDatabase.profiles(ctx)
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
            toast(act.getText(R.string.profile_not_activated).toString() + " '" + selected.name + "'")
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
        val host = hostFragment ?: return
        val act = activity ?: return
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
        val dao = AppDatabase.profiles(ctx)
        for (p in detected) {
            p.setId(dao.addProfile(p))
            toast(ctx.getText(R.string.profile_added).toString() + " '" + p.name + "'")
        }
        reloadProfiles()
    }

    private fun onDevicesDetected(found: ArrayList<Profile>) {
        val act = activity ?: return
        onDetectProgressChanged?.invoke(false)
        detectedProfiles = found
        val builder = MaterialAlertDialogBuilder(act)
        builder.setTitle(R.string.autodiscover_dreamboxes)
        if (found.isNotEmpty()) {
            val items = Array(found.size) { i ->
                String.format("%s (%s)", found[i].name, found[i].host)
            }
            builder
                .setItems(items) { _, which ->
                    selected = found[which]
                    editProfile()
                }
                .setPositiveButton(R.string.reload) { _, _ ->
                    detectedProfiles = null
                    detectDevices()
                }
                .setNegativeButton(R.string.add_all) { _, _ -> addAllDetectedDevices() }
        } else {
            builder.setMessage(R.string.autodiscovery_failed)
            builder.setNeutralButton(android.R.string.ok) { _, _ -> }
        }
        builder.show()
    }

    private fun onItemClicked(id: Int): Boolean {
        val mph = activity as? MultiPaneHandler
        return when (id) {
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
                mph?.showDialogFragment(
                    PositiveNegativeDialog.newInstance(
                        selected.name,
                        R.string.confirm_delete_profile,
                        android.R.string.yes,
                        Statics.ACTION_DELETE_CONFIRMED,
                        android.R.string.no,
                        Statics.ACTION_NONE,
                    ),
                    "dialog_delete_profile_confirm",
                )
                true
            }
            else -> false
        }
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        if (action == Statics.ACTION_DELETE_CONFIRMED) {
            val ctx = context ?: return
            AppDatabase.profiles(ctx).deleteProfile(selected)
            toast(ctx.getString(R.string.profile_deleted) + " '" + selected.name + "'")
            reloadProfiles()
            selected = Profile.getDefault()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.profiles, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return onItemClicked(menuItem.itemId)
    }
}
