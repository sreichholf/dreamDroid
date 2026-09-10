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

/**
 * Hosts Compose [androidx.navigation.compose.NavHost] in the phone detail pane.
 * Device Info is the first leaf route; drawer re-selection uses [navigateToRoute]
 * instead of replacing this fragment (Phase 2.1d).
 */
class PhoneNavHostFragment : BaseFragment() {

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

    /** Nested destination fragment currently shown under the NavHost (if any). */
    fun getActiveLeaf(): Fragment? {
        return childFragmentManager.findFragmentById(R.id.phone_nav_device_info_slot)
            ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.DEVICE_INFO)
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
        controller.navigate(route) {
            launchSingleTop = true
            popUpTo(controller.graph.startDestinationId) {
                inclusive = false
            }
        }
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
