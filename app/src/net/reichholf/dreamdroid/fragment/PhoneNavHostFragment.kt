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
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.abs.BaseFragment
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment
import android.content.DialogInterface
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost
import net.reichholf.dreamdroid.ui.nav.navigateDrawerRoot
import net.reichholf.dreamdroid.ui.nav.navigateDrawerSettings
import net.reichholf.dreamdroid.ui.nav.navigateToBackup
import net.reichholf.dreamdroid.ui.nav.navigateToEpgSearch
import net.reichholf.dreamdroid.ui.nav.navigateToServiceEpg
import net.reichholf.dreamdroid.ui.timers.TimerEditSession

/**
 * Hosts Compose [androidx.navigation.compose.NavHost] in the phone detail pane.
 * Migrated drawer leaves include Device Info through hub (`ServiceListPager`). Drawer selection uses
 * [navigateToRoute] when this host is already shown; [ARG_START_ROUTE] picks the first leaf.
 *
 * [net.reichholf.dreamdroid.activities.abs.BaseActivity] only delivers [onActivityResult] to
 * top-level fragments; forward to the active leaf (Profiles edit, EPG bouquet picker, Zap).
 */
class PhoneNavHostFragment : BaseFragment(), MultiChoiceDialog.MultiChoiceDialogListener {

    companion object {
        const val ARG_START_ROUTE = "phone_nav_start_route"
        private const val STATE_PICK_REQUEST_CODES = "phone_nav_pick_request_codes"
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

    /**
     * Optional dialog-action sink for Compose destinations that replaced nested Fragments
     * (e.g. Current Service). [MainActivity] forwards via [getActiveLeaf] → this host.
     */
    var composeDialogActionListener: ActionDialog.DialogActionListener? = null

    /**
     * Optional activity-result sink for Compose destinations (e.g. Zap bouquet pick).
     * [deliverPickResult] prefers this when [getActiveLeaf] is null.
     */
    fun interface ActivityResultListener {
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    var composeActivityResultListener: ActivityResultListener? = null

    /**
     * Optional MultiChoice sink for Compose destinations (timer edit tags/repeatings).
     * [MainActivity] forwards via getDetailContentFragment → this host when leaf is null.
     */
    var composeMultiChoiceListener: MultiChoiceDialog.MultiChoiceDialogListener? = null

    /** Stack of pending onActivityResult request codes (nested edit → service pick). */
    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var profileEditArgs: Bundle? = null
    private var profileEditTag: String = PhoneNavRoutes.PROFILE_EDIT
    private var timerEditArgs: Bundle? = null
    private var timerEditTag: String = PhoneNavRoutes.TIMER_EDIT
    private var timerEditSession: TimerEditSession? = null
    private var pendingProfileEditRequested: Boolean = false
    private var pendingProfileEdit: Profile? = null
    private var pendingTimerEdit: ExtendedHashMap? = null
    private var pendingTimerCreate: Boolean = false
    private var pendingEpgSearchQuery: String? = null
    private val profileEditRemountState = MutableStateFlow(0)
    private val timerEditRemountState = MutableStateFlow(0)
    private val epgRemountState = MutableStateFlow(0)
    private val epgSearchRemountState = MutableStateFlow(0)

    /** Bumps when profile edit args change while already on [PhoneNavRoutes.PROFILE_EDIT]. */
    val profileEditRemountEpoch: Int
        get() = profileEditRemountState.value

    /** Bumps when timer edit args change while already on [PhoneNavRoutes.TIMER_EDIT]. */
    val timerEditRemountEpoch: Int
        get() = timerEditRemountState.value

    fun profileEditRemountFlow(): StateFlow<Int> = profileEditRemountState.asStateFlow()

    fun timerEditRemountFlow(): StateFlow<Int> = timerEditRemountState.asStateFlow()

    fun epgRemountFlow(): StateFlow<Int> = epgRemountState.asStateFlow()

    fun epgSearchRemountFlow(): StateFlow<Int> = epgSearchRemountState.asStateFlow()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            discardResultRequestCodeForCurrentRoute()
            navController?.popBackStack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mShouldRetainInstance = false
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            resultRequestCodes.clear()
            savedInstanceState.getIntArray(STATE_PICK_REQUEST_CODES)?.forEach { resultRequestCodes.addLast(it) }
            profileEditArgs = savedInstanceState.getBundle(STATE_PROFILE_EDIT_ARGS)
            profileEditTag = savedInstanceState.getString(STATE_PROFILE_EDIT_TAG, PhoneNavRoutes.PROFILE_EDIT)
            timerEditArgs = savedInstanceState.getBundle(STATE_TIMER_EDIT_ARGS)
            timerEditTag = savedInstanceState.getString(STATE_TIMER_EDIT_TAG, PhoneNavRoutes.TIMER_EDIT)
            timerEditSession = TimerEditSession.fromSavedState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_PICK_REQUEST_CODES, resultRequestCodes.toIntArray())
        profileEditArgs?.let { outState.putBundle(STATE_PROFILE_EDIT_ARGS, it) }
        outState.putString(STATE_PROFILE_EDIT_TAG, profileEditTag)
        timerEditArgs?.let { outState.putBundle(STATE_TIMER_EDIT_ARGS, it) }
        outState.putString(STATE_TIMER_EDIT_TAG, timerEditTag)
        timerEditSession?.writeTo(outState)
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

    /** Args for EPG bouquet destination (default TV bouquet from drawer). */
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
            // Phase 2.7b/c: Compose destinations (no nested Fragment).
            route == PhoneNavRoutes.DEVICE_INFO -> null
            route == PhoneNavRoutes.SIGNAL -> null
            route == PhoneNavRoutes.BACKUP -> null
            route == PhoneNavRoutes.CURRENT -> null
            route == PhoneNavRoutes.SCREENSHOT -> null
            route == PhoneNavRoutes.ZAP -> null
            route == PhoneNavRoutes.REMOTE -> null
            route == PhoneNavRoutes.SETTINGS -> null
            route == PhoneNavRoutes.PROFILES -> null
            route == PhoneNavRoutes.PROFILE_EDIT -> null
            route == PhoneNavRoutes.EPG -> null
            route == PhoneNavRoutes.SERVICE_EPG || route.startsWith("service_epg") -> null
            route == PhoneNavRoutes.EPG_SEARCH || route.startsWith("epg_search") -> null
            route == PhoneNavRoutes.PICK_SERVICE -> null
            route == PhoneNavRoutes.TIMER_EDIT -> null
            route == PhoneNavRoutes.TIMER_SERVICE_PICK -> null
            route == PhoneNavRoutes.HUB ->
                childFragmentManager.findFragmentById(R.id.phone_nav_hub_slot)
                    ?: childFragmentManager.findFragmentByTag(PhoneNavRoutes.HUB)
            else -> null
        }
    }

    fun attachNavController(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, _, _ ->
            backCallback.isEnabled = controller.previousBackStackEntry != null
        }
        backCallback.isEnabled = controller.previousBackStackEntry != null
        flushPendingNavigations()
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
        resultRequestCodes.clear()
        clearTimerEditSession()
        if (route == PhoneNavRoutes.SETTINGS) {
            controller.navigateDrawerSettings()
            return true
        }
        controller.navigateDrawerRoot(route)
        return true
    }

    /** Push nested Backup (Settings → Backup). Back returns to Settings. */
    fun navigateToBackup(): Boolean {
        val controller = navController ?: return false
        controller.navigateToBackup()
        return true
    }

    /**
     * Open EPG with bouquet args. Remounts when already on the EPG route so
     * [EpgBouquetDestination] reloads from fresh host args.
     */
    fun navigateToEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController ?: return false
        // Drawer-style EPG open drops any nested edit/pick back stack entries.
        if (controller.currentDestination?.route != PhoneNavRoutes.EPG) {
            resultRequestCodes.clear()
        }
        val args = arguments ?: Bundle().also { arguments = it }
        args.putString(Event.KEY_SERVICE_REFERENCE, serviceReference)
        args.putString(Event.KEY_SERVICE_NAME, serviceName)
        if (controller.currentDestination?.route == PhoneNavRoutes.EPG) {
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        controller.navigateDrawerRoot(PhoneNavRoutes.EPG)
        return true
    }

    /**
     * Push service EPG onto the NavHost back stack (hub → service EPG).
     * Typed string args; back pops to the previous drawer leaf.
     */
    fun navigateToServiceEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController ?: return false
        controller.navigateToServiceEpg(serviceReference.orEmpty(), serviceName)
        return true
    }

    /**
     * Push EPG search onto the NavHost back stack.
     * Resubmitting the same query bumps a remount epoch so results reload.
     */
    fun navigateToEpgSearch(query: String?): Boolean {
        val controller = navController ?: return false
        val q = query.orEmpty()
        if (q.isEmpty()) return false
        val onSearch = controller.currentDestination?.route == PhoneNavRoutes.EPG_SEARCH
            || controller.currentDestination?.route?.startsWith("epg_search") == true
        if (onSearch) {
            epgSearchRemountState.value = epgSearchRemountState.value + 1
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
        pushResultRequestCode(requestCode)
        controller.navigate(PhoneNavRoutes.PICK_SERVICE)
        return true
    }

    /**
     * Pop the nested picker and forward [onActivityResult] to the prior leaf
     * (Zap / EPG bouquet). Used instead of [Fragment.setTargetFragment] under NavHost.
     */


    /** Queue profile edit until [attachNavController] (host was just mounted). */
    fun queueProfileEdit(profile: Profile?) {
        pendingProfileEditRequested = true
        pendingProfileEdit = profile
        flushPendingNavigations()
    }

    /** Queue timer edit until [attachNavController] (host was just mounted). */
    fun queueTimerEdit(timer: ExtendedHashMap, create: Boolean) {
        pendingTimerEdit = timer
        pendingTimerCreate = create
        flushPendingNavigations()
    }

    /** Queue EPG search until [attachNavController] (cold SEARCH / empty detail pane). */
    fun queueEpgSearch(query: String) {
        pendingEpgSearchQuery = query
        flushPendingNavigations()
    }

    private fun flushPendingNavigations() {
        if (navController == null) return
        if (pendingProfileEditRequested) {
            pendingProfileEditRequested = false
            val profile = pendingProfileEdit
            pendingProfileEdit = null
            navigateToProfileEdit(profile)
        }
        val timer = pendingTimerEdit
        if (timer != null) {
            pendingTimerEdit = null
            val create = pendingTimerCreate
            pendingTimerCreate = false
            navigateToTimerEdit(timer, create)
        }
        val searchQuery = pendingEpgSearchQuery
        if (searchQuery != null) {
            pendingEpgSearchQuery = null
            navigateToEpgSearch(searchQuery)
        }
    }

    fun profileEditRouteTag(): String = profileEditTag

    fun profileEditLeafArguments(): Bundle {
        return profileEditArgs ?: Bundle()
    }

    /**
     * Push profile create/edit. Result goes through [deliverPickResult] with
     * [Statics.REQUEST_EDIT_PROFILE]. ProfilesDestination reloads on re-enter.
     */
    fun navigateToProfileEdit(profile: Profile?): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(Statics.REQUEST_EDIT_PROFILE)
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
        if (controller.currentDestination?.route == PhoneNavRoutes.PROFILE_EDIT) {
            profileEditRemountState.value = profileEditRemountState.value + 1
            return true
        }
        controller.navigate(PhoneNavRoutes.PROFILE_EDIT)
        return true
    }

    /** Current NavHost route, or [startRoute] if the controller is not attached. */
    fun currentRoute(): String {
        return navController?.currentDestination?.route ?: startRoute()
    }

    fun popNavBackStack(): Boolean {
        return navController?.popBackStack() ?: false
    }


    fun timerEditRouteTag(): String = timerEditTag

    fun timerEditLeafArguments(): Bundle = timerEditArgs ?: Bundle()

    /**
     * Reuse the in-memory edit session across service-pick navigation; create from args
     * when starting a new edit or after remount.
     */
    fun obtainTimerEditSession(routeTag: String, remountEpoch: Int): TimerEditSession {
        val existing = timerEditSession
        if (existing != null &&
            existing.routeTag == routeTag &&
            existing.remountEpoch == remountEpoch
        ) {
            return existing
        }
        val session = TimerEditSession.fromArgs(timerEditLeafArguments(), routeTag, remountEpoch)
        timerEditSession = session
        return session
    }

    fun clearTimerEditSession() {
        timerEditSession = null
    }

    /**
     * Push timer create/edit. Service pick uses [navigateToTimerServicePick].
     * Result goes through [deliverPickResult] with [Statics.REQUEST_EDIT_TIMER].
     */
    fun navigateToTimerEdit(timer: ExtendedHashMap, create: Boolean): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(Statics.REQUEST_EDIT_TIMER)
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
        timerEditSession = null
        if (controller.currentDestination?.route == PhoneNavRoutes.TIMER_EDIT) {
            timerEditRemountState.value = timerEditRemountState.value + 1
            return true
        }
        controller.navigate(PhoneNavRoutes.TIMER_EDIT)
        return true
    }

    private fun pushResultRequestCode(code: Int) {
        resultRequestCodes.addLast(code)
    }

    /** True when this route pushed a pending [resultRequestCodes] entry. */
    private fun isResultDestination(route: String?): Boolean {
        if (route == null) return false
        return route == PhoneNavRoutes.PICK_SERVICE
            || route == PhoneNavRoutes.PROFILE_EDIT
            || route == PhoneNavRoutes.TIMER_EDIT
            || route == PhoneNavRoutes.TIMER_SERVICE_PICK
    }

    /**
     * System/gesture back pops the NavHost without [deliverPickResult]; drop the
     * matching pending request code so a later finish still sees the outer code
     * (e.g. timer edit after canceling service pick).
     */
    private fun discardResultRequestCodeForCurrentRoute() {
        val route = navController?.currentDestination?.route
        if (isResultDestination(route) && resultRequestCodes.isNotEmpty()) {
            resultRequestCodes.removeLast()
            if (route == PhoneNavRoutes.TIMER_EDIT) {
                clearTimerEditSession()
            }
        }
    }

    /**
     * Push nested timer service pick onto the NavHost back stack (from [TimerEditFragment]).
     * Pushes [Statics.REQUEST_PICK_SERVICE] without clearing the pending edit request code.
     */
    fun navigateToTimerServicePick(): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(Statics.REQUEST_PICK_SERVICE)
        controller.navigate(PhoneNavRoutes.TIMER_SERVICE_PICK)
        return true
    }

    fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = if (resultRequestCodes.isEmpty()) -1 else resultRequestCodes.removeLast()
        if (code == Statics.REQUEST_EDIT_TIMER) {
            clearTimerEditSession()
        }
        if (!controller.popBackStack()) return
        view?.post {
            val composeListener = composeActivityResultListener
            if (code >= 0 && composeListener != null) {
                composeListener.onActivityResult(code, resultCode, data)
                return@post
            }
            val leaf = getActiveLeaf()
            // Profile/timer edit finish with a null Intent; service/bouquet pick send extras.
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

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val listener = composeDialogActionListener
        if (listener != null) {
            listener.onDialogAction(action, details, dialogTag)
            return
        }
        super.onDialogAction(action, details, dialogTag)
    }

    override fun onMultiChoiceDialogSelection(
        dialogTag: String?,
        dialog: DialogInterface?,
        selected: Array<out Int>?,
    ) {
        composeMultiChoiceListener?.onMultiChoiceDialogSelection(dialogTag, dialog, selected)
    }

    override fun onMultiChoiceDialogFinish(dialogTag: String?, result: Int) {
        composeMultiChoiceListener?.onMultiChoiceDialogFinish(dialogTag, result)
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
