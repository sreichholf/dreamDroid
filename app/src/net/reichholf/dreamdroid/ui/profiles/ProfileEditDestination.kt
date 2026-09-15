package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import android.content.Context
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
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.nav.NavExtras

/**
 * Phase 2.7e: Profile create/edit as a direct Compose NavHost destination.
 * Remounts when [PhoneNavHostFragment.profileEditRouteTag] / remount epoch changes.
 */
@Composable
fun ProfileEditDestination(hostFragment: PhoneNavHostFragment, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val remount = hostFragment.profileEditRemountEpoch
    val tag = hostFragment.profileEditRouteTag()
    val args = hostFragment.profileEditLeafArguments()

    @Suppress("DEPRECATION")
    val extras = args.getSerializable(NavExtras.DATA) as? Profile
    val action = args.getString(NavExtras.ACTION)
    val initialProfile = remember(tag, remount) {
        when {
            Intent.ACTION_EDIT == action && extras != null -> extras
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
        val outcome = persistEditedProfile(context, currentProfile)
        toast(outcome.message)
        if (outcome.saved) {
            hostFragment.deliverPickResult(Activity.RESULT_OK, null)
        }
    }

    val menuProvider = remember {
        object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.save, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
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
        modifier = modifier
    )
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
    val dao = AppDatabase.profiles(context)
    val id = profile.id ?: 0
    if (id > 0) {
        dao.updateProfile(profile)
        if (profile.id == DreamDroid.getCurrentProfile().id) {
            DreamDroid.setCurrentProfile(profile)
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
