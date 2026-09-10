package net.reichholf.dreamdroid.fragment

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
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost
import net.reichholf.dreamdroid.ui.nav.navigateDrawerRoot

/**
 * Hosts Compose [androidx.navigation.compose.NavHost] in the phone detail pane.
 * Migrated leaves: Device Info, Signal. Drawer selection uses [navigateToRoute] when this
 * host is already shown; [ARG_START_ROUTE] picks the first leaf when mounting.
 */
class PhoneNavHostFragment : BaseFragment() {

    companion object {
        const val ARG_START_ROUTE = "phone_nav_start_route"

        @JvmStatic
        fun newInstance(startRoute: String): PhoneNavHostFragment {
            return PhoneNavHostFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_ROUTE, startRoute)
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
