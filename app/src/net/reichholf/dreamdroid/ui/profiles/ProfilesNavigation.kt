package net.reichholf.dreamdroid.ui.profiles

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes

/**
 * Shared entry for opening profile edit from Profiles destination or MainActivity.
 */
object ProfilesNavigation {
    @JvmStatic
    fun openProfileEdit(activity: Activity, profile: Profile?) {
        if (activity !is FragmentActivity) {
            return
        }
        val detail = activity.supportFragmentManager.findFragmentById(R.id.detail_view)
        if (detail is PhoneNavHostFragment && detail.navigateToProfileEdit(profile)) {
            return
        }
        val host = PhoneNavHostFragment.newInstance(PhoneNavRoutes.PROFILES)
        host.queueProfileEdit(profile)
        when (activity) {
            is MainActivity -> activity.showDetails(host)
            is MultiPaneHandler -> activity.showDetails(host)
        }
    }
}
