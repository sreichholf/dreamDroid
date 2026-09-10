package net.reichholf.dreamdroid.fragment

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.NavHostController
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.fragment.abs.BaseFragment
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost
import net.reichholf.dreamdroid.ui.nav.navigateDrawerRoot
import net.reichholf.dreamdroid.ui.nav.navigateToEpgSearch
import net.reichholf.dreamdroid.ui.nav.navigateToServiceEpg

/**
 * Hosts Compose [androidx.navigation.compose.NavHost] in the phone detail pane.
 * Migrated drawer leaves include Device Info through hub (`ServiceListPager`). Drawer selection uses
 * [navigateToRoute] when this host is already shown; [ARG_START_ROUTE] picks the first leaf.
 *
 * [net.reichholf.dreamdroid.activities.abs.BaseActivity] only delivers [onActivityResult] to
 * top-level fragments; forward to the active leaf (Profiles edit, EPG bouquet picker, Zap).
 */
class PhoneNavHostFragment : BaseFragment() {

    companion object {
        const val ARG_START_ROUTE = "phone_nav_start_route"
        private const val STATE_PICK_REQUEST_CODE = "phone_nav_pick_request_code"
        private const val STATE_PROFILE_EDIT_ARGS = "phone_nav_profile_edit_args"
        private const val STATE_PROFILE_EDIT_TAG = "phone_nav_profile_edit_tag"
        private const val STATE_TIMER_EDIT_ARGS = "phone_nav_timer_edit_args"
        private const val STATE_TIMER_EDIT_TAG = "phone_nav_timer_edit_tag"

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

    private var pickRequestCode: Int = -1
    private var profileEditArgs: Bundle? = null
    private var profileEditTag: String = PhoneNavRoutes.PROFILE_EDIT
    private var timerEditArgs: Bundle? = null
    private var timerEditTag: String = PhoneNavRoutes.TIMER_EDIT

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            navController?.popBackStack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mShouldRetainInstance = false
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            pickRequestCode = savedInstanceState.getInt(STATE_PICK_REQUEST_CODE, -1)
            profileEditArgs = savedInstanceState.getBundle(STATE_PROFILE_EDIT_ARGS)
            profileEditTag = savedInstanceState.getString(STATE_PROFILE_EDIT_TAG, PhoneNavRoutes.PROFILE_EDIT)
            timerEditArgs = savedInstanceState.getBundle(STATE_TIMER_EDIT_ARGS)
            timerEditTag = savedInstanceState.getString(STATE_TIMER_EDIT_TAG, PhoneNavRoutes.TIMER_EDIT)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PICK_REQUEST_CODE, pickRequestCode)
        profileEditArgs?.let { outState.putBundle(STATE_PROFILE_EDIT_ARGS, it) }
        outState.putString(STATE_PROFILE_EDIT_TAG, profileEditTag)
        timerEditArgs?.let { outState.putBundle(STATE_TIMER_EDIT_ARGS, it) }
        outState.putString(STATE_TIMER_EDIT_TAG, timerEditTag)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
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
        return when {
            route == PhoneNavRoutes.DEVICE_INFO ->
                childFragmentManager.findFragmentById(R.id.phone_nav_device_info_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.DEVICE_INFO)
            route == PhoneNavRoutes.SIGNAL ->
                childFragmentManager.findFragmentById(R.id.phone_nav_signal_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SIGNAL)
            route == PhoneNavRoutes.SCREENSHOT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_screenshot_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SCREENSHOT)
            route == PhoneNavRoutes.CURRENT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_current_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.CURRENT)
            route == PhoneNavRoutes.ZAP ->
                childFragmentManager.findFragmentById(R.id.phone_nav_zap_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.ZAP)
            route == PhoneNavRoutes.BACKUP ->
                childFragmentManager.findFragmentById(R.id.phone_nav_backup_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.BACKUP)
            route == PhoneNavRoutes.PROFILES ->
                childFragmentManager.findFragmentById(R.id.phone_nav_profiles_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.PROFILES)
            route == PhoneNavRoutes.EPG ->
                childFragmentManager.findFragmentById(R.id.phone_nav_epg_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.EPG)
            route == PhoneNavRoutes.REMOTE ->
                childFragmentManager.findFragmentById(R.id.phone_nav_remote_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.REMOTE)
            route == PhoneNavRoutes.SETTINGS ->
                childFragmentManager.findFragmentById(R.id.phone_nav_settings_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SETTINGS)
            route == PhoneNavRoutes.HUB ->
                childFragmentManager.findFragmentById(R.id.phone_nav_hub_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.HUB)
            route == PhoneNavRoutes.SERVICE_EPG || route.startsWith("service_epg") ->
                childFragmentManager.findFragmentById(R.id.phone_nav_service_epg_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.SERVICE_EPG)
            route == PhoneNavRoutes.EPG_SEARCH || route.startsWith("epg_search") ->
                childFragmentManager.findFragmentById(R.id.phone_nav_epg_search_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.EPG_SEARCH)
            route == PhoneNavRoutes.PICK_SERVICE ->
                childFragmentManager.findFragmentById(R.id.phone_nav_pick_service_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.PICK_SERVICE)
            route == PhoneNavRoutes.PROFILE_EDIT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_profile_edit_slot)
                    ?: childFragmentManager.findFragmentByTag(profileEditTag)
            route == PhoneNavRoutes.TIMER_EDIT ->
                childFragmentManager.findFragmentById(R.id.phone_nav_timer_edit_slot)
                    ?: childFragmentManager.findFragmentByTag(timerEditTag)
            else -> null
        }
    }

    fun attachNavController(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, _, _ ->
            backCallback.isEnabled = controller.previousBackStackEntry != null
        }
        backCallback.isEnabled = controller.previousBackStackEntry != null
    }

    fun detachNavController(controller: NavHostController) {
        if (navController === controller) {
            navController = null
            backCallback.isEnabled = false
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

    /**
     * Push nested service EPG onto the NavHost back stack (hub → service EPG).
     * Typed string args; back pops to the previous drawer leaf.
     */
    fun navigateToServiceEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController ?: return false
        controller.navigateToServiceEpg(serviceReference.orEmpty(), serviceName)
        return true
    }

    /**
     * Push nested EPG search onto the NavHost back stack.
     * Typed query string; back pops to the previous leaf.
     * Resubmitting the same query remounts the leaf so results reload.
     */
    fun navigateToEpgSearch(query: String?): Boolean {
        val controller = navController ?: return false
        val q = query.orEmpty()
        if (q.isEmpty()) return false
        val existing = childFragmentManager.findFragmentById(R.id.phone_nav_epg_search_slot)
            ?: childFragmentManager.findFragmentByTag("epg_search:$q")
        val onSearch = controller.currentDestination?.route == PhoneNavRoutes.EPG_SEARCH
            || controller.currentDestination?.route?.startsWith("epg_search") == true
        if (existing != null && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction().remove(existing).commitNow()
        }
        if (onSearch && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.phone_nav_epg_search_slot,
                    EpgSearchFragment().apply {
                        arguments = Bundle().apply {
                            putString(android.app.SearchManager.QUERY, q)
                        }
                    },
                    "epg_search:$q",
                )
                .commitNow()
        }
        controller.navigateToEpgSearch(q)
        return true
    }

    /**
     * Push nested bouquet picker onto the NavHost back stack.
     * Result is delivered via [deliverPickResult] when the picker finishes.
     */
    fun navigateToPickBouquet(requestCode: Int): Boolean {
        val controller = navController ?: return false
        pickRequestCode = requestCode
        controller.navigate(PhoneNavRoutes.PICK_SERVICE)
        return true
    }

    /**
     * Pop the nested picker and forward [onActivityResult] to the prior leaf
     * (Zap / EPG bouquet). Used instead of [Fragment.setTargetFragment] under NavHost.
     */

    fun profileEditRouteTag(): String = profileEditTag

    fun profileEditLeafArguments(): Bundle {
        return profileEditArgs ?: Bundle()
    }

    /**
     * Push nested profile create/edit. Result goes through [deliverPickResult] with
     * [Statics.REQUEST_EDIT_PROFILE] so [ProfileListFragment] can reload.
     */
    fun navigateToProfileEdit(profile: Profile?): Boolean {
        val controller = navController ?: return false
        pickRequestCode = Statics.REQUEST_EDIT_PROFILE
        val data = ExtendedHashMap()
        data.put("action", Intent.ACTION_EDIT)
        if (profile != null) {
            data.put("profile", profile)
        }
        profileEditArgs = Bundle().apply {
            putSerializable(BaseHttpFragment.sData, data)
        }
        profileEditTag = if (profile != null) {
            "profile_edit:${profile.id}"
        } else {
            "profile_edit:new"
        }
        val existing = childFragmentManager.findFragmentById(R.id.phone_nav_profile_edit_slot)
            ?: childFragmentManager.findFragmentByTag(profileEditTag)
        if (existing != null && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction().remove(existing).commitNow()
        }
        if (controller.currentDestination?.route == PhoneNavRoutes.PROFILE_EDIT) {
            if (!childFragmentManager.isStateSaved) {
                childFragmentManager.beginTransaction()
                    .replace(
                        R.id.phone_nav_profile_edit_slot,
                        ProfileEditFragment().apply { arguments = profileEditLeafArguments() },
                        profileEditTag,
                    )
                    .commitNow()
            }
            return true
        }
        controller.navigate(PhoneNavRoutes.PROFILE_EDIT)
        return true
    }


    fun timerEditRouteTag(): String = timerEditTag

    fun timerEditLeafArguments(): Bundle = timerEditArgs ?: Bundle()

    /**
     * Push nested timer create/edit. Service pick stays on [SimpleToolbarFragmentActivity].
     * Result goes through [deliverPickResult] with [Statics.REQUEST_EDIT_TIMER].
     */
    fun navigateToTimerEdit(timer: ExtendedHashMap, create: Boolean): Boolean {
        val controller = navController ?: return false
        pickRequestCode = Statics.REQUEST_EDIT_TIMER
        val data = ExtendedHashMap()
        data.put("timer", timer)
        data.put("action", if (create) DreamDroid.ACTION_CREATE else Intent.ACTION_EDIT)
        timerEditArgs = Bundle().apply {
            putSerializable(BaseHttpFragment.sData, data)
        }
        val ref = timer.getString(Timer.KEY_REFERENCE).orEmpty()
        val begin = timer.getString(Timer.KEY_BEGIN).orEmpty()
        timerEditTag = if (create) {
            "timer_edit:new:$begin"
        } else {
            "timer_edit:$ref:$begin"
        }
        val existing = childFragmentManager.findFragmentById(R.id.phone_nav_timer_edit_slot)
            ?: childFragmentManager.findFragmentByTag(timerEditTag)
        if (existing != null && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction().remove(existing).commitNow()
        }
        if (controller.currentDestination?.route == PhoneNavRoutes.TIMER_EDIT) {
            if (!childFragmentManager.isStateSaved) {
                childFragmentManager.beginTransaction()
                    .replace(
                        R.id.phone_nav_timer_edit_slot,
                        TimerEditFragment().apply { arguments = timerEditLeafArguments() },
                        timerEditTag,
                    )
                    .commitNow()
            }
            return true
        }
        controller.navigate(PhoneNavRoutes.TIMER_EDIT)
        return true
    }

    fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = pickRequestCode
        pickRequestCode = -1
        if (!controller.popBackStack()) return
        view?.post {
            val leaf = getActiveLeaf()
            // Profile edit finishes with a null Intent; bouquet pick sends extras.
            if (code >= 0 && leaf != null) {
                leaf.onActivityResult(code, resultCode, data)
            }
        }
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
