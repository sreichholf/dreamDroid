package net.reichholf.dreamdroid.fragment

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.NavHostController
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.abs.BaseFragment
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost
import net.reichholf.dreamdroid.ui.nav.navigateDrawerRoot

/**
 * Hosts Compose [androidx.navigation.compose.NavHost] in the phone detail pane.
 * Migrated drawer leaves include Device Info through Profiles and EPG. Drawer selection uses
 * [navigateToRoute] when this host is already shown; [ARG_START_ROUTE] picks the first leaf.
 *
 * [net.reichholf.dreamdroid.activities.abs.BaseActivity] only delivers [onActivityResult] to
 * top-level fragments; forward to the active leaf (Profiles edit, EPG bouquet picker, Zap).
 */
class PhoneNavHostFragment : BaseFragment() {

    companion object {
        const val ARG_START_ROUTE = "phone_nav_start_route"

        @JvmStatic
        fun newInstance(startRoute: String): PhoneNavHostFragment {
            return newInstance(startRoute, null)
        }

        @JvmStatic
        fun newInstance(startRoute: String, leafExtras: Bundle?): PhoneNavHostFragment {
            return PhoneNavHostFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_ROUTE, startRoute)
                    if (leafExtras != null) {
                        putAll(leafExtras)
                    }
                }
            }
        }
    }

    @Volatile
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        mShouldRetainInstance = false
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            bindPhoneNavHost(this@PhoneNavHostFragment)
        }
    }

    fun startRoute(): String {
        return arguments?.getString(ARG_START_ROUTE) ?: PhoneNavRoutes.DEVICE_INFO
    }

    /** Args for nested [EpgBouquetFragment] (default TV bouquet from drawer). */
    fun epgLeafArguments(): Bundle {
        return Bundle().apply {
            putString(
                Event.KEY_SERVICE_REFERENCE,
                arguments?.getString(Event.KEY_SERVICE_REFERENCE),
            )
            putString(
                Event.KEY_SERVICE_NAME,
                arguments?.getString(Event.KEY_SERVICE_NAME),
            )
        }
    }

    /** Nested destination fragment for the current NavHost route (if any). */
    fun getActiveLeaf(): Fragment? {
        val route = navController?.currentDestination?.route ?: startRoute()
        return when (route) {
            PhoneNavRoutes.DEVICE_INFO ->
                childFragmentManager.findFragmentById(R.id.phone_nav_device_info_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.DEVICE_INFO)
            PhoneNavRoutes.SIGNAL ->
                childFragmentManager.findFragmentById(R.id.phone_nav_signal_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SIGNAL)
            PhoneNavRoutes.SCREENSHOT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_screenshot_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SCREENSHOT)
            PhoneNavRoutes.CURRENT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_current_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.CURRENT)
            PhoneNavRoutes.ZAP ->
                childFragmentManager.findFragmentById(R.id.phone_nav_zap_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.ZAP)
            PhoneNavRoutes.BACKUP ->
                childFragmentManager.findFragmentById(R.id.phone_nav_backup_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.BACKUP)
            PhoneNavRoutes.PROFILES ->
                childFragmentManager.findFragmentById(R.id.phone_nav_profiles_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.PROFILES)
            PhoneNavRoutes.EPG ->
                childFragmentManager.findFragmentById(R.id.phone_nav_epg_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.EPG)
            PhoneNavRoutes.REMOTE ->
                childFragmentManager.findFragmentById(R.id.phone_nav_remote_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.REMOTE)
            else -> null
        }
    }

    fun attachNavController(controller: NavHostController) {
        navController = controller
    }

    fun detachNavController(controller: NavHostController) {
        if (navController === controller) {
            navController = null
        }
    }

    /**
     * Navigate within the hosted [androidx.navigation.NavHost] without replacing this
     * fragment. No-op if the controller is not ready yet (first show still uses
     * [net.reichholf.dreamdroid.activities.MainActivity.showDetails]).
     *
     * @return true if a navigation was requested
     */
    fun navigateToRoute(route: String): Boolean {
        val controller = navController ?: return false
        controller.navigateDrawerRoot(route)
        return true
    }

    /**
     * Open EPG with bouquet args. Remounts the nested leaf when args change so
     * [EpgBouquetFragment] reads a fresh Bundle. When already on the EPG route,
     * `launchSingleTop` would no-op — replace the child fragment directly.
     */
    fun navigateToEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController ?: return false
        val args = arguments ?: Bundle().also { arguments = it }
        args.putString(Event.KEY_SERVICE_REFERENCE, serviceReference)
        args.putString(Event.KEY_SERVICE_NAME, serviceName)
        val existing = childFragmentManager.findFragmentById(R.id.phone_nav_epg_slot)
            ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.EPG)
        if (existing != null && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction().remove(existing).commitNow()
        }
        if (controller.currentDestination?.route == PhoneNavRoutes.EPG) {
            if (!childFragmentManager.isStateSaved) {
                childFragmentManager.beginTransaction()
                    .replace(
                        R.id.phone_nav_epg_slot,
                        EpgBouquetFragment().apply { arguments = epgLeafArguments() },
                        PhoneNavRoutes.EPG,
                    )
                    .commitNow()
            }
            return true
        }
        controller.navigateDrawerRoot(PhoneNavRoutes.EPG)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        getActiveLeaf()?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDrawerOpened() {
        (getActiveLeaf() as? ActivityCallbackHandler)?.onDrawerOpened()
    }

    override fun onDrawerClosed() {
        (getActiveLeaf() as? ActivityCallbackHandler)?.onDrawerClosed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return (getActiveLeaf() as? ActivityCallbackHandler)?.onKeyDown(keyCode, event) ?: false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return (getActiveLeaf() as? ActivityCallbackHandler)?.onKeyUp(keyCode, event) ?: false
    }
}
