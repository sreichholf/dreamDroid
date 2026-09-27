package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.preference.PreferenceManager
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

internal const val PHONE_NAV_SCHEMA_KEY = "dreamdroid_nav_schema"
internal const val PHONE_NAV_SCHEMA_VERSION = 2

/**
 * Activity-scoped ViewModel for phone NavHost state (result stacks, queued extras,
 * drawer navigate).
 */
class PhoneNavHostState(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application),
    PhoneNavHandle {

    @Volatile
    private var navController: NavHostController? = null
    internal var shellDestinationBarController: ShellDestinationBarController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val plainAccess = SavedStatePlainAccess(savedStateHandle)
    private var attachedLifecycleOwner: LifecycleOwner? = null
    private var highlighter: DrawerRouteHighlighter? = null
    private var startRouteSaved: Boolean = false

    override val lifecycleOwner: LifecycleOwner
        get() = attachedLifecycleOwner ?: error("Phone nav host is not attached to an activity")

    fun attach(owner: LifecycleOwner, drawerHighlighter: DrawerRouteHighlighter) {
        attachedLifecycleOwner = owner
        highlighter = drawerHighlighter
    }

    fun detach() {
        attachedLifecycleOwner = null
        highlighter = null
    }

    override fun onCleared() {
        detach()
        super.onCleared()
    }

    override var composeDialogActionListener: DialogActionListener? = null
    override var composeActivityResultListener: PhoneNavHandle.ActivityResultListener? = null
    private var pendingComposeActivityResult: PendingComposeActivityResult? = null
    private var pendingComposeActivityData: Intent? = null

    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var startRouteValue: String = PhoneNavRoutes.DEVICE_INFO
    private var pendingProfileEditRequested: Boolean = false
    private var pendingProfileEdit: Profile? = null
    private var pendingTimerEdit: Timer? = null
    private var pendingTimerCreate: Boolean = false
    private var pendingEpgSearchQuery: String? = null
    private var pendingSleepTimer: SleepTimerRoute? = null
    private var pendingOpenSleepTimer: Boolean = false
    private var pendingMultiEpg: MultiEpg? = null
    private var pendingChangelog: Boolean = false
    private var pendingProfileCheck: Boolean = false
    private var pendingDrawerRoot: Any? = null
    private var pendingAbout: Boolean = false
    private var pendingPower: Boolean = false
    private var pendingSendMessage: Boolean = false
    private var pendingBackup: Boolean = false
    private val profileCheckUiState = MutableStateFlow<ProfileCheckUi>(
        ProfileCheckUi.Checking("")
    )
    private val leaveConfirmRequestedState = MutableStateFlow(false)
    private val needsReceiverRequestedState = MutableStateFlow(false)
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

    init {
        val bag = readPhoneNavStateBag(plainAccess)
        startRouteSaved = bag.hasSavedStartRoute()
        startRouteValue = bag.startRoute ?: PhoneNavRoutes.DEVICE_INFO
        bag.pickRequestCodes.forEach { resultRequestCodes.addLast(it) }
    }

    fun hasSavedStartRoute(): Boolean = startRouteSaved

    /** False until this process has accepted the type-safe route schema. */
    fun hasNavSchema(): Boolean =
        savedStateHandle.get<Int>(PHONE_NAV_SCHEMA_KEY) == PHONE_NAV_SCHEMA_VERSION

    fun markNavSchema() {
        savedStateHandle[PHONE_NAV_SCHEMA_KEY] = PHONE_NAV_SCHEMA_VERSION
    }

    fun setStartRoute(route: String) {
        startRouteValue = route
        startRouteSaved = true
        persistPlain()
    }

    private fun persistPlain() {
        PhoneNavStateBag(
            startRoute = if (startRouteSaved) startRouteValue else null,
            pickRequestCodes = resultRequestCodes.toList()
        ).writePlain(plainAccess)
    }

    override fun startRoute(): String = startRouteValue

    override fun attachNavController(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, dest, _ ->
            val previous = controller.previousBackStackEntry?.destination?.route
            highlighter?.highlightDrawerForRoute(dest.route, previous)
            val activity = attachedLifecycleOwner as? Activity
            applyShellDestinationBarForRoute(
                dest.route,
                shellDestinationBarController,
                activity?.findViewById(R.id.shell_destination_nav),
                activity?.findViewById(R.id.shell_destination_rail)
            )
        }
        flushPendingNavigations()
    }

    override fun detachNavController(controller: NavHostController) {
        if (navController === controller) {
            navController = null
        }
    }

    override fun navigateToRoute(route: Any): Boolean {
        clearResultRequestCodes()
        val destination = normalizeRoute(route)
        val controller = navController
        if (controller == null) {
            pendingDrawerRoot = destination
            return true
        }
        if (destination is Settings) {
            controller.navigateDrawerSettings()
            return true
        }
        controller.navigateDrawerRoot(destination)
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
        pendingSleepTimer = timer.toSleepTimerRoute()
        val controller = navController
        if (controller == null) {
            pendingOpenSleepTimer = true
            return true
        }
        controller.navigateToSleepTimer(pendingSleepTimer ?: SleepTimerRoute())
        pendingSleepTimer = null
        return true
    }

    override fun queueSleepTimer(timer: SleepTimer) {
        pendingSleepTimer = timer.toSleepTimerRoute()
        pendingOpenSleepTimer = true
        flushPendingNavigations()
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

    override fun needsReceiverRequestedFlow(): StateFlow<Boolean> =
        needsReceiverRequestedState.asStateFlow()

    override fun requestNeedsReceiver() {
        needsReceiverRequestedState.value = true
    }

    override fun clearNeedsReceiver() {
        needsReceiverRequestedState.value = false
    }

    override fun updateProfileCheckUi(ui: ProfileCheckUi) {
        profileCheckUiState.value = ui
    }

    override fun isOnProfileCheckRoute(): Boolean =
        routeKey(navController?.currentDestination?.route) == PhoneNavRoutes.PROFILE_CHECK

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

    override fun navigateAboveProfileCheck(route: Any): Boolean {
        val controller = navController ?: return false
        controller.navigateAboveProfileCheck(route)
        return true
    }

    override fun navigateReplacingProfileCheck(route: Any): Boolean {
        val controller = navController ?: return false
        controller.navigateReplacingProfileCheck(route)
        return true
    }

    override fun navigateToEpg(
        serviceReference: String?,
        serviceName: String?,
        timeSec: Long?
    ): Boolean {
        val route = Epg(
            serviceRef = serviceReference.orEmpty(),
            serviceName = serviceName.orEmpty(),
            timeSec = timeSec ?: PhoneNavRoutes.ABSENT_TIME_SEC
        )
        val controller = navController
        if (controller == null) {
            pendingDrawerRoot = route
            return true
        }
        val currentRoute = routeKey(controller.currentDestination?.route)
        val previous = routeKey(controller.previousBackStackEntry?.destination?.route)
        if (currentRoute == PhoneNavRoutes.MULTI_EPG && previous == PhoneNavRoutes.EPG) {
            clearResultRequestCodes()
            controller.popBackStack()
            controller.replaceRoute<Epg>(route)
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            clearResultRequestCodes()
            controller.navigateDrawerRoot(route)
        } else {
            controller.replaceRoute<Epg>(route)
        }
        epgRemountState.value = epgRemountState.value + 1
        return true
    }

    override fun navigateToDrawerEpg(): Boolean {
        val ctx = lifecycleOwner as Context
        val profile = ProfileRepository.get().requireCurrent()
        val ref = profile.defaultBouquetTv
        val name = profile.defaultBouquetTvName
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (!DrawerEpgMode.isMulti(prefs)) {
            return navigateToEpg(ref, name)
        }
        val listRoute = Epg(serviceRef = ref.orEmpty(), serviceName = name.orEmpty())
        val controller = navController
        if (controller == null) {
            pendingDrawerRoot = listRoute
            pendingMultiEpg = MultiEpg(serviceRef = ref.orEmpty(), serviceName = name.orEmpty())
            return true
        }
        val currentRoute = routeKey(controller.currentDestination?.route)
        val previous = routeKey(controller.previousBackStackEntry?.destination?.route)
        if (DrawerEpgMode.isNestedOnListEpg(currentRoute, previous)) {
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            clearResultRequestCodes()
            controller.navigateDrawerRoot(listRoute)
        }
        return navigateToMultiEpg(ref, name)
    }

    override fun navigateToMultiEpg(
        serviceReference: String?,
        serviceName: String?,
        focusedServiceRef: String?,
        timeSec: Long?
    ): Boolean {
        val route = MultiEpg(
            serviceRef = serviceReference.orEmpty(),
            serviceName = serviceName.orEmpty(),
            focusedServiceRef = focusedServiceRef.orEmpty(),
            timeSec = timeSec ?: PhoneNavRoutes.ABSENT_TIME_SEC
        )
        val controller = navController
        if (controller == null) {
            pendingMultiEpg = route
            return true
        }
        val onMulti = routeKey(controller.currentDestination?.route) == PhoneNavRoutes.MULTI_EPG
        if (!onMulti) {
            clearResultRequestCodes()
            controller.navigate(route) { launchSingleTop = true }
        } else {
            controller.replaceRoute<MultiEpg>(route)
        }
        epgRemountState.value = epgRemountState.value + 1
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
        val route = EpgSearch(query = q)
        val onSearch = routeKey(controller.currentDestination?.route) == PhoneNavRoutes.EPG_SEARCH
        if (onSearch && q.isEmpty()) {
            return true
        }
        if (onSearch) {
            controller.replaceRoute(route)
            epgSearchRemountState.value = epgSearchRemountState.value + 1
            return true
        }
        controller.navigateToEpgSearch(q)
        return true
    }

    override fun navigateToPickBouquet(requestCode: Int): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(requestCode)
        controller.navigate(PickService)
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
            val route = pendingSleepTimer ?: SleepTimerRoute()
            pendingSleepTimer = null
            navController?.navigateToSleepTimer(route)
        }
        if (pendingChangelog) {
            pendingChangelog = false
            navController?.navigateToChangelog()
        }
        if (pendingProfileCheck) {
            pendingProfileCheck = false
            navController?.navigateToProfileCheck()
        }
        val multi = pendingMultiEpg
        if (multi != null) {
            pendingMultiEpg = null
            navigateToMultiEpg(
                multi.serviceRef.ifEmpty { null },
                multi.serviceName.ifEmpty { null },
                multi.focusedOrNull(),
                multi.timeOrNull()
            )
        }
    }

    override fun navigateToProfileEdit(profile: Profile?): Boolean {
        val controller = navController
        if (controller == null) {
            queueProfileEdit(profile)
            return true
        }
        pushResultRequestCode(Statics.REQUEST_EDIT_PROFILE)
        val route = profile.toProfileEditRoute()
        if (routeKey(controller.currentDestination?.route) == PhoneNavRoutes.PROFILE_EDIT) {
            controller.replaceRoute(route)
            profileEditRemountState.value = profileEditRemountState.value + 1
            return true
        }
        controller.navigate(route)
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

    override fun navigateToTimerEdit(timer: Timer, create: Boolean): Boolean {
        val controller = navController
        if (controller == null) {
            queueTimerEdit(timer, create)
            return true
        }
        pushResultRequestCode(Statics.REQUEST_EDIT_TIMER)
        val route = TimerEdit.from(timer, create)
        if (routeKey(controller.currentDestination?.route) == PhoneNavRoutes.TIMER_EDIT) {
            controller.replaceRoute(route)
            timerEditRemountState.value = timerEditRemountState.value + 1
            return true
        }
        controller.navigate(route)
        return true
    }

    private fun pushResultRequestCode(code: Int) {
        resultRequestCodes.addLast(code)
        persistPlain()
    }

    private fun popResultRequestCode(): Int {
        val code = resultRequestCodes.removeLast()
        persistPlain()
        return code
    }

    private fun clearResultRequestCodes() {
        if (resultRequestCodes.isEmpty()) {
            return
        }
        resultRequestCodes.clear()
        persistPlain()
    }

    private fun isResultDestination(route: String?): Boolean {
        val key = routeKey(route) ?: return false
        return key == PhoneNavRoutes.PICK_SERVICE ||
            key == PhoneNavRoutes.PROFILE_EDIT ||
            key == PhoneNavRoutes.TIMER_EDIT ||
            key == PhoneNavRoutes.TIMER_SERVICE_PICK
    }

    private fun discardResultRequestCodeForCurrentRoute() {
        val route = navController?.currentDestination?.route
        if (isResultDestination(route) && resultRequestCodes.isNotEmpty()) {
            popResultRequestCode()
        }
    }

    override fun navigateToTimerServicePick(): Boolean {
        val controller = navController ?: return false
        pushResultRequestCode(Statics.REQUEST_PICK_SERVICE)
        controller.navigate(TimerServicePick)
        return true
    }

    override fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = if (resultRequestCodes.isEmpty()) -1 else popResultRequestCode()
        if (!controller.popBackStack()) return
        mainHandler.post {
            deliverComposeActivityResult(code, resultCode, data)
        }
    }

    override fun dispatchPendingComposeActivityResult() {
        val pending = takePendingComposeActivityResult(
            listenerAttached = composeActivityResultListener != null,
            pending = pendingComposeActivityResult
        ) ?: return
        val data = pendingComposeActivityData
        pendingComposeActivityResult = null
        pendingComposeActivityData = null
        composeActivityResultListener?.onActivityResult(
            pending.requestCode,
            pending.resultCode,
            data
        )
    }

    override fun onHostActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        deliverComposeActivityResult(requestCode, resultCode, data)
    }

    private fun deliverComposeActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode < 0) {
            return
        }
        val incoming = PendingComposeActivityResult(requestCode, resultCode)
        val held = holdComposeActivityResultIfDetached(
            listenerAttached = composeActivityResultListener != null,
            incoming = incoming
        )
        if (held != null) {
            pendingComposeActivityResult = held
            pendingComposeActivityData = data
            return
        }
        pendingComposeActivityResult = null
        pendingComposeActivityData = null
        composeActivityResultListener?.onActivityResult(requestCode, resultCode, data)
    }

    private class SavedStatePlainAccess(private val handle: SavedStateHandle) :
        PhoneNavPlainAccess {
        override fun contains(key: String): Boolean = handle.contains(key)

        override fun getString(key: String): String? = handle.get<String>(key)

        override fun putString(key: String, value: String?) {
            if (value == null) {
                handle.remove<String>(key)
            } else {
                handle[key] = value
            }
        }

        override fun getIntList(key: String): List<Int>? {
            val stored = handle.get<IntArray>(key) ?: return null
            return stored.toList()
        }

        override fun putIntList(key: String, value: List<Int>?) {
            if (value == null) {
                handle.remove<IntArray>(key)
            } else {
                handle[key] = value.toIntArray()
            }
        }
    }
}
