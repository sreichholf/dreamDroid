package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.timers.TimerEditSession

/**
 * Activity-owned phone NavHost state (result stacks, queued extras, drawer navigate).
 */
class PhoneNavHostState(
    override val lifecycleOwner: LifecycleOwner,
    private val highlighter: DrawerRouteHighlighter
) : PhoneNavHandle {

    companion object {
        private const val STATE_START_ROUTE = "phone_nav_start_route"
        private const val STATE_PICK_REQUEST_CODES = "phone_nav_pick_request_codes"
        private const val STATE_PROFILE_EDIT_ARGS = "phone_nav_profile_edit_args"
        private const val STATE_PROFILE_EDIT_TAG = "phone_nav_profile_edit_tag"
        private const val STATE_TIMER_EDIT_ARGS = "phone_nav_timer_edit_args"
        private const val STATE_TIMER_EDIT_TAG = "phone_nav_timer_edit_tag"
        private const val STATE_EPG_REF = "phone_nav_epg_ref"
        private const val STATE_EPG_NAME = "phone_nav_epg_name"
    }

    @Volatile
    private var navController: NavHostController? = null
    internal var shellDestinationBarController: ShellDestinationBarController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override var composeDialogActionListener: DialogActionListener? = null
    override var composeActivityResultListener: PhoneNavHandle.ActivityResultListener? = null

    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var startRouteValue: String = PhoneNavRoutes.DEVICE_INFO
    private var epgServiceReference: String? = null
    private var epgServiceName: String? = null
    private var profileEditArgs: Bundle? = null
    private var profileEditTag: String = PhoneNavRoutes.PROFILE_EDIT
    private var timerEditArgs: Bundle? = null
    private var timerEditTag: String = PhoneNavRoutes.TIMER_EDIT
    private var timerEditSession: TimerEditSession? = null
    private var pendingProfileEditRequested: Boolean = false
    private var pendingProfileEdit: Profile? = null
    private var pendingTimerEdit: Timer? = null
    private var pendingTimerCreate: Boolean = false
    private var pendingEpgSearchQuery: String? = null
    private var pendingSleepTimerArgs: SleepTimerNavArgs? = null
    private var pendingOpenSleepTimer: Boolean = false
    private var pendingChangelog: Boolean = false
    private var pendingProfileCheck: Boolean = false
    private var pendingDrawerRoot: String? = null
    private var pendingAbout: Boolean = false
    private var pendingPower: Boolean = false
    private var pendingSendMessage: Boolean = false
    private var pendingBackup: Boolean = false
    private val profileCheckUiState = MutableStateFlow<ProfileCheckUi>(
        ProfileCheckUi.Checking("")
    )
    private val leaveConfirmRequestedState = MutableStateFlow(false)
    private val profileEditRemountState = MutableStateFlow(0)
    private val timerEditRemountState = MutableStateFlow(0)
    private val epgRemountState = MutableStateFlow(0)
    private val epgSearchRemountState = MutableStateFlow(0)

    override val profileEditRemountEpoch: Int
        get() = profileEditRemountState.value

    override val timerEditRemountEpoch: Int
        get() = timerEditRemountState.value

    override fun profileEditRemountFlow(): StateFlow<Int> = profileEditRemountState.asStateFlow()

    override fun timerEditRemountFlow(): StateFlow<Int> = timerEditRemountState.asStateFlow()

    override fun epgRemountFlow(): StateFlow<Int> = epgRemountState.asStateFlow()

    override fun epgSearchRemountFlow(): StateFlow<Int> = epgSearchRemountState.asStateFlow()

    override fun onActiveProfileChanged() {
        epgRemountState.value = epgRemountState.value + 1
    }

    fun setStartRoute(route: String) {
        startRouteValue = route
    }

    fun restoreState(savedInstanceState: Bundle) {
        resultRequestCodes.clear()
        savedInstanceState.getIntArray(STATE_PICK_REQUEST_CODES)?.forEach {
            resultRequestCodes.addLast(it)
        }
        startRouteValue = savedInstanceState.getString(
            STATE_START_ROUTE,
            PhoneNavRoutes.DEVICE_INFO
        )
        epgServiceReference = savedInstanceState.getString(STATE_EPG_REF)
        epgServiceName = savedInstanceState.getString(STATE_EPG_NAME)
        profileEditArgs = savedInstanceState.getBundle(STATE_PROFILE_EDIT_ARGS)
        profileEditTag =
            savedInstanceState.getString(STATE_PROFILE_EDIT_TAG, PhoneNavRoutes.PROFILE_EDIT)
        timerEditArgs = savedInstanceState.getBundle(STATE_TIMER_EDIT_ARGS)
        timerEditTag =
            savedInstanceState.getString(STATE_TIMER_EDIT_TAG, PhoneNavRoutes.TIMER_EDIT)
        timerEditSession = TimerEditSession.fromSavedState(savedInstanceState)
    }

    fun saveState(outState: Bundle) {
        outState.putIntArray(STATE_PICK_REQUEST_CODES, resultRequestCodes.toIntArray())
        outState.putString(STATE_START_ROUTE, startRouteValue)
        outState.putString(STATE_EPG_REF, epgServiceReference)
        outState.putString(STATE_EPG_NAME, epgServiceName)
        profileEditArgs?.let { outState.putBundle(STATE_PROFILE_EDIT_ARGS, it) }
        outState.putString(STATE_PROFILE_EDIT_TAG, profileEditTag)
        timerEditArgs?.let { outState.putBundle(STATE_TIMER_EDIT_ARGS, it) }
        outState.putString(STATE_TIMER_EDIT_TAG, timerEditTag)
        timerEditSession?.writeTo(outState)
    }

    override fun startRoute(): String = startRouteValue

    override fun epgLeafArguments(): Bundle = Bundle().apply {
        putString(Event.KEY_SERVICE_REFERENCE, epgServiceReference)
        putString(Event.KEY_SERVICE_NAME, epgServiceName)
    }

    override fun attachNavController(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, dest, _ ->
            val previous = controller.previousBackStackEntry?.destination?.route
            highlighter.highlightDrawerForRoute(dest.route, previous)
            applyShellDestinationBarForRoute(
                dest.route,
                shellDestinationBarController,
                (lifecycleOwner as? Activity)?.findViewById(R.id.shell_destination_nav)
            )
        }
        flushPendingNavigations()
    }

    override fun detachNavController(controller: NavHostController) {
        if (navController === controller) {
            navController = null
        }
    }

    override fun navigateToRoute(route: String): Boolean {
        resultRequestCodes.clear()
        clearTimerEditSession()
        val controller = navController
        if (controller == null) {
            pendingDrawerRoot = route
            return true
        }
        if (route == PhoneNavRoutes.SETTINGS) {
            controller.navigateDrawerSettings()
            return true
        }
        controller.navigateDrawerRoot(route)
        return true
    }

    override fun navigateToBackup(): Boolean {
        val controller = navController
        if (controller == null) {
            pendingBackup = true
            return true
        }
        controller.navigateToBackup()
        return true
    }

    override fun navigateToAbout(): Boolean {
        val controller = navController
        if (controller == null) {
            pendingAbout = true
            return true
        }
        controller.navigateToAbout()
        return true
    }

    override fun navigateToPower(): Boolean {
        val controller = navController
        if (controller == null) {
            pendingPower = true
            return true
        }
        controller.navigateToPower()
        return true
    }

    override fun navigateToSendMessage(): Boolean {
        val controller = navController
        if (controller == null) {
            pendingSendMessage = true
            return true
        }
        controller.navigateToSendMessage()
        return true
    }

    override fun navigateToSleepTimer(timer: SleepTimer): Boolean {
        pendingSleepTimerArgs = SleepTimerNavArgs.from(timer)
        val controller = navController
        if (controller == null) {
            pendingOpenSleepTimer = true
            return true
        }
        controller.navigateToSleepTimer()
        return true
    }

    override fun queueSleepTimer(timer: SleepTimer) {
        pendingSleepTimerArgs = SleepTimerNavArgs.from(timer)
        pendingOpenSleepTimer = true
        flushPendingNavigations()
    }

    override fun consumeSleepTimerArgs(): SleepTimerNavArgs {
        val args = pendingSleepTimerArgs ?: SleepTimerNavArgs.defaults()
        pendingSleepTimerArgs = null
        return args
    }

    override fun navigateToChangelog(): Boolean {
        val controller = navController
        if (controller == null) {
            pendingChangelog = true
            return true
        }
        controller.navigateToChangelog()
        return true
    }

    override fun queueChangelog() {
        pendingChangelog = true
        flushPendingNavigations()
    }

    override fun profileCheckUiFlow(): StateFlow<ProfileCheckUi> = profileCheckUiState.asStateFlow()

    override fun connectionStatusFlow(): StateFlow<ConnectionStatus> =
        SessionConnectionHolder.shared.status

    override fun leaveConfirmRequestedFlow(): StateFlow<Boolean> =
        leaveConfirmRequestedState.asStateFlow()

    override fun requestLeaveConfirm() {
        leaveConfirmRequestedState.value = true
    }

    override fun clearLeaveConfirm() {
        leaveConfirmRequestedState.value = false
    }

    override fun updateProfileCheckUi(ui: ProfileCheckUi) {
        profileCheckUiState.value = ui
    }

    override fun isOnProfileCheckRoute(): Boolean =
        navController?.currentDestination?.route == PhoneNavRoutes.PROFILE_CHECK

    override fun navigateToProfileCheck(ui: ProfileCheckUi): Boolean {
        updateProfileCheckUi(ui)
        val controller = navController
        if (controller == null) {
            pendingProfileCheck = true
            return true
        }
        controller.navigateToProfileCheck()
        return true
    }

    override fun queueProfileCheck(ui: ProfileCheckUi) {
        updateProfileCheckUi(ui)
        pendingProfileCheck = true
        flushPendingNavigations()
    }

    override fun navigateAboveProfileCheck(route: String): Boolean {
        val controller = navController ?: return false
        controller.navigateAboveProfileCheck(route)
        return true
    }

    override fun navigateReplacingProfileCheck(route: String): Boolean {
        val controller = navController ?: return false
        controller.navigateReplacingProfileCheck(route)
        return true
    }

    override fun navigateToEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController
        epgServiceReference = serviceReference
        epgServiceName = serviceName
        if (controller == null) {
            pendingDrawerRoot = PhoneNavRoutes.EPG
            return true
        }
        if (controller.currentDestination?.route != PhoneNavRoutes.EPG) {
            resultRequestCodes.clear()
        }
        if (controller.currentDestination?.route == PhoneNavRoutes.EPG) {
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        controller.navigateDrawerRoot(PhoneNavRoutes.EPG)
        return true
    }

    override fun navigateToMultiEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController
        epgServiceReference = serviceReference
        epgServiceName = serviceName
        if (controller == null) {
            pendingDrawerRoot = PhoneNavRoutes.MULTI_EPG
            return true
        }
        if (controller.currentDestination?.route != PhoneNavRoutes.MULTI_EPG) {
            resultRequestCodes.clear()
        }
        epgRemountState.value = epgRemountState.value + 1
        if (controller.currentDestination?.route == PhoneNavRoutes.MULTI_EPG) {
            return true
        }
        controller.navigateDrawerRoot(PhoneNavRoutes.MULTI_EPG)
        return true
    }

    override fun navigateToServiceEpg(serviceReference: String?, serviceName: String?): Boolean {
        val controller = navController ?: return false
        controller.navigateToServiceEpg(serviceReference.orEmpty(), serviceName)
        return true
    }

    override fun navigateToEpgSearch(query: String?): Boolean {
        val q = query.orEmpty()
        val controller = navController
        if (controller == null) {
            pendingEpgSearchQuery = q
            return true
        }
        val onSearch = controller.currentDestination?.route == PhoneNavRoutes.EPG_SEARCH ||
            controller.currentDestination?.route?.startsWith("epg_search") == true
        if (onSearch && q.isEmpty()) {
            return true
        }
        if (onSearch) {
            epgSearchRemountState.value = epgSearchRemountState.value + 1
        }
        controller.navigateToEpgSearch(q)
        return true
    }

    override fun navigateToPickBouquet(requestCode: Int): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(requestCode)
        controller.navigate(PhoneNavRoutes.PICK_SERVICE)
        return true
    }

    override fun queueProfileEdit(profile: Profile?) {
        pendingProfileEditRequested = true
        pendingProfileEdit = profile
        flushPendingNavigations()
    }

    override fun queueTimerEdit(timer: Timer, create: Boolean) {
        pendingTimerEdit = timer
        pendingTimerCreate = create
        flushPendingNavigations()
    }

    override fun queueEpgSearch(query: String) {
        pendingEpgSearchQuery = query
        flushPendingNavigations()
    }

    private fun flushPendingNavigations() {
        if (navController == null) return
        val drawerRoot = pendingDrawerRoot
        if (drawerRoot != null) {
            pendingDrawerRoot = null
            navigateToRoute(drawerRoot)
        }
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
        if (pendingBackup) {
            pendingBackup = false
            navController?.navigateToBackup()
        }
        if (pendingAbout) {
            pendingAbout = false
            navController?.navigateToAbout()
        }
        if (pendingPower) {
            pendingPower = false
            navController?.navigateToPower()
        }
        if (pendingSendMessage) {
            pendingSendMessage = false
            navController?.navigateToSendMessage()
        }
        if (pendingOpenSleepTimer) {
            pendingOpenSleepTimer = false
            navController?.navigateToSleepTimer()
        }
        if (pendingChangelog) {
            pendingChangelog = false
            navController?.navigateToChangelog()
        }
        if (pendingProfileCheck) {
            pendingProfileCheck = false
            navController?.navigateToProfileCheck()
        }
    }

    override fun profileEditRouteTag(): String = profileEditTag

    override fun profileEditLeafArguments(): Bundle = profileEditArgs ?: Bundle()

    override fun navigateToProfileEdit(profile: Profile?): Boolean {
        val controller = navController
        if (controller == null) {
            queueProfileEdit(profile)
            return true
        }
        pushResultRequestCode(Statics.REQUEST_EDIT_PROFILE)
        val data = Bundle().apply {
            putString(NavExtras.ACTION, Intent.ACTION_EDIT)
            if (profile != null) {
                putSerializable(NavExtras.DATA, profile)
            }
        }
        profileEditArgs = data
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

    override fun popNavBackStack(): Boolean {
        val controller = navController ?: return false
        if (controller.previousBackStackEntry == null) {
            return false
        }
        discardResultRequestCodeForCurrentRoute()
        return controller.popBackStack()
    }

    override fun timerEditRouteTag(): String = timerEditTag

    override fun timerEditLeafArguments(): Bundle = timerEditArgs ?: Bundle()

    override fun obtainTimerEditSession(routeTag: String, remountEpoch: Int): TimerEditSession {
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

    override fun clearTimerEditSession() {
        timerEditSession = null
    }

    override fun navigateToTimerEdit(timer: Timer, create: Boolean): Boolean {
        val controller = navController
        if (controller == null) {
            queueTimerEdit(timer, create)
            return true
        }
        pushResultRequestCode(Statics.REQUEST_EDIT_TIMER)
        timerEditArgs = Bundle().apply {
            putSerializable(NavExtras.DATA, timer)
            putString(
                NavExtras.ACTION,
                if (create) DreamDroid.ACTION_CREATE else Intent.ACTION_EDIT
            )
        }
        val ref = timer.reference
        val begin = timer.begin
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

    private fun isResultDestination(route: String?): Boolean {
        if (route == null) return false
        return route == PhoneNavRoutes.PICK_SERVICE ||
            route == PhoneNavRoutes.PROFILE_EDIT ||
            route == PhoneNavRoutes.TIMER_EDIT ||
            route == PhoneNavRoutes.TIMER_SERVICE_PICK
    }

    private fun discardResultRequestCodeForCurrentRoute() {
        val route = navController?.currentDestination?.route
        if (isResultDestination(route) && resultRequestCodes.isNotEmpty()) {
            resultRequestCodes.removeLast()
            if (route == PhoneNavRoutes.TIMER_EDIT) {
                clearTimerEditSession()
            }
        }
    }

    override fun navigateToTimerServicePick(): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(Statics.REQUEST_PICK_SERVICE)
        controller.navigate(PhoneNavRoutes.TIMER_SERVICE_PICK)
        return true
    }

    override fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = if (resultRequestCodes.isEmpty()) -1 else resultRequestCodes.removeLast()
        if (code == Statics.REQUEST_EDIT_TIMER) {
            clearTimerEditSession()
        }
        if (!controller.popBackStack()) return
        mainHandler.post {
            val composeListener = composeActivityResultListener
            if (code >= 0 && composeListener != null) {
                composeListener.onActivityResult(code, resultCode, data)
            }
        }
    }

    override fun onHostActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        composeActivityResultListener?.onActivityResult(requestCode, resultCode, data)
    }
}
