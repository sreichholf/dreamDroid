package net.reichholf.dreamdroid.ui.nav

import android.util.SparseArray
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.drawer.DrawerListState

/**
 * Drawer click → phone [PhoneNavHandle] bridge. Power, sleep timer, and send
 * message run on [ShellViewModel]; this type only navigates and dispatches.
 */
open class NavigationHelper(activity: MainActivity, protected val drawerState: DrawerListState) {
    var activity: MainActivity = activity

    protected var selectedItemId: Int = drawerState.selectedItemId

    protected fun getMainActivity(): MainActivity = activity

    /**
     * Open a migrated phone NavHost leaf via the activity-owned [PhoneNavHandle].
     */
    protected fun navigatePhoneNavRoot(route: Any) {
        getMainActivity().phoneNav.navigateToRoute(route)
    }

    fun onProfileChanged() {
        getMainActivity().phoneNav.onActiveProfileChanged()
    }

    protected fun setSelectedItem(itemId: Int) {
        if (isDialogItem(itemId)) return
        if (itemId == R.id.menu_navigation_profiles) {
            drawerState.clearSelection()
            return
        }
        drawerState.select(itemId)
        selectedItemId = itemId
    }

    fun navigateTo(itemId: Int) {
        onNavigationItemClick(itemId)
    }

    protected fun isDialogItem(itemId: Int): Boolean {
        for (id in dialogItemIds) {
            if (id == itemId) return true
        }
        return false
    }

    protected fun onNavigationItemClick(itemId: Int): Boolean {
        setSelectedItem(itemId)

        val navRoot = navRootRoutes.get(itemId)
        if (navRoot != null) {
            navigatePhoneNavRoot(navRoot)
            getMainActivity().showContent()
            return true
        }

        when (itemId) {
            R.id.menu_navigation_message -> {
                getMainActivity().phoneNav.runOnlineOnly {
                    getMainActivity().phoneNav.navigateToSendMessage()
                }
            }

            Statics.ITEM_TOGGLE_STANDBY,
            Statics.ITEM_RESTART_GUI,
            Statics.ITEM_REBOOT,
            Statics.ITEM_SHUTDOWN ->
                getMainActivity().phoneNav.runOnlineOnly {
                    getMainActivity().shellActions.onPowerMenuAction(itemId)
                }

            R.id.menu_navigation_power -> {
                getMainActivity().phoneNav.runOnlineOnly {
                    getMainActivity().phoneNav.navigateToPower()
                }
            }

            R.id.menu_navigation_about -> {
                getMainActivity().phoneNav.navigateToAbout()
            }

            R.id.menu_navigation_changelog -> {
                getMainActivity().showChangeLog(false)
            }

            R.id.menu_navigation_sleeptimer ->
                getMainActivity().phoneNav.runOnlineOnly {
                    getMainActivity().shellActions.loadSleepTimerForDialog()
                }

            R.id.menu_navigation_epg ->
                navigateToEpg()

            R.id.menu_navigation_backup -> {
                getMainActivity().phoneNav.navigateToBackup()
            }
        }
        getMainActivity().showContent()
        return !isDialogItem(itemId)
    }

    /**
     * Drawer EPG opens the last list/MultiEPG mode and seeds default bouquet extras.
     */
    protected fun navigateToEpg() {
        getMainActivity().phoneNav.navigateToDrawerEpg()
    }

    fun setAvailableFeatures() {
        drawerState.sleepTimerAvailable = DreamDroid.featureSleepTimer()
    }

    fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        onNavigationItemClick(action)
    }

    companion object {
        protected val dialogItemIds: IntArray = intArrayOf(
            R.id.menu_navigation_sleeptimer,
            R.id.menu_navigation_message,
            R.id.menu_navigation_power,
            R.id.menu_navigation_about,
            R.id.menu_navigation_changelog
        )

        /** Drawer menu ids that open a PhoneNavHost root (no extras). EPG is separate. */
        private val navRootRoutes: SparseArray<Any> = SparseArray<Any>().apply {
            put(R.id.menu_navigation_services, Hub)
            put(R.id.menu_navigation_tools, Tools)
            put(R.id.menu_navigation_current, Hub)
            put(R.id.menu_navigation_remote, Remote)
            put(R.id.menu_navigation_settings, Settings)
            put(R.id.menu_navigation_profiles, Profiles)
            put(R.id.menu_navigation_zap, Zap)
        }
    }
}
