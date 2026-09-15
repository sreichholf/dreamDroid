package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.activities.MainActivity

/**
 * Shared entry for opening profile edit from Profiles destination or MainActivity.
 */
object ProfilesNavigation {
    fun openProfileEdit(activity: Activity, profile: Profile?) {
        val handle = (activity as? MainActivity)?.phoneNav ?: return
        handle.navigateToProfileEdit(profile)
    }
}
