package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
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
import androidx.core.view.MenuProvider
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Phase 2.7e: Profile create/edit as a direct Compose NavHost destination.
 * Remounts when [PhoneNavHostFragment.profileEditRouteTag] / remount epoch changes.
 */
@Composable
fun ProfileEditDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remount = hostFragment.profileEditRemountEpoch
    val tag = hostFragment.profileEditRouteTag()
    val args = hostFragment.profileEditLeafArguments()
    @Suppress("DEPRECATION")
    val extras = args.getSerializable(NavExtras.DATA) as? ExtendedHashMap
    val initialProfile = remember(tag, remount) {
        val fromExtras = extras?.get("profile") as? Profile
        when {
            Intent.ACTION_EDIT == extras?.get("action") && fromExtras != null -> fromExtras
            else -> Profile.getDefault()
        }
    }
    val editState = remember(initialProfile) { ProfileEditState.fromProfile(initialProfile) }
    var currentProfile by remember(initialProfile) { mutableStateOf(initialProfile) }

    fun toast(message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun save() {
        editState.applyTo(currentProfile)
        val dao = AppDatabase.profiles(context)
        val id = currentProfile.id ?: 0
        if (id > 0) {
            if (currentProfile.host.isNullOrEmpty()) {
                toast(context.getText(R.string.host_empty))
                return
            }
            if (currentProfile.streamHost == null) {
                currentProfile.streamHost = ""
            }
            dao.updateProfile(currentProfile)
            if (currentProfile.id == DreamDroid.getCurrentProfile().id) {
                DreamDroid.setCurrentProfile(currentProfile)
            }
            toast(context.getText(R.string.profile_updated).toString() + " '" + currentProfile.name + "'")
            hostFragment.deliverPickResult(Activity.RESULT_OK, null)
        } else {
            currentProfile.setId(dao.addProfile(currentProfile))
            toast(context.getText(R.string.profile_added).toString() + " '" + currentProfile.name + "'")
            hostFragment.deliverPickResult(Activity.RESULT_OK, null)
        }
    }

    val menuProvider = remember {
        object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.save, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    Statics.ITEM_SAVE -> {
                        save()
                        true
                    }
                    Statics.ITEM_CANCEL -> {
                        hostFragment.deliverPickResult(Activity.RESULT_CANCELED, null)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    DisposableEffect(hostFragment, menuProvider, tag, remount) {
        (context as? AppCompatActivity)?.title = context.getString(R.string.edit_profile)
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(menuProvider, hostFragment.viewLifecycleOwner)
        onDispose {
            activity?.removeMenuProvider(menuProvider)
        }
    }

    LaunchedEffect(tag, remount) {
        (context as? AppCompatActivity)?.title = context.getString(R.string.edit_profile)
    }

    ProfileEditScreen(
        state = editState,
        saveLabel = context.getString(R.string.save),
        onSave = { save() },
        modifier = modifier,
    )
}
