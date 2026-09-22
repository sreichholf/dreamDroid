package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.preference.PreferenceManager
import java.util.ArrayDeque
import java.util.ArrayList
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
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.timers.TimerEditSession

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
    private var pauseObserver: LifecycleEventObserver? = null
    private var startRouteSaved: Boolean = false

    override val lifecycleOwner: LifecycleOwner
        get() = attachedLifecycleOwner ?: error("Phone nav host is not attached to an activity")

    fun attach(owner: LifecycleOwner, drawerHighlighter: DrawerRouteHighlighter) {
        if (attachedLifecycleOwner !== owner) {
            clearPauseObserver()
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    // Form fields mutate the live session after obtain(). Flush before
                    // SavedStateHandle is written so process death keeps the edit.
                    persistTimerEditSession()
                }
            }
            owner.lifecycle.addObserver(observer)
            pauseObserver = observer
            attachedLifecycleOwner = owner
        }
        highlighter = drawerHighlighter
    }

    fun detach() {
        clearPauseObserver()
        timerEditSession?.handle = null
        timerEditSession?.context = null
        attachedLifecycleOwner = null
        highlighter = null
    }

    private fun clearPauseObserver() {
        val owner = attachedLifecycleOwner
        val observer = pauseObserver
        if (owner != null && observer != null) {
            owner.lifecycle.removeObserver(observer)
        }
        pauseObserver = null
    }

    override fun onCleared() {
        detach()
        super.onCleared()
    }

    override var composeDialogActionListener: DialogActionListener? = null
    override var composeActivityResultListener: PhoneNavHandle.ActivityResultListener? = null

    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var startRouteValue: String = PhoneNavRoutes.DEVICE_INFO
    private var epgServiceReference: String? = null
    private var epgServiceName: String? = null
    private var epgFocusedServiceRef: String? = null
    private var epgTimeSec: Long? = null
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
    private var pendingNestedMultiEpg: Boolean = false
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
        profileEditTag = bag.profileEditTag
        timerEditTag = bag.timerEditTag
        epgServiceReference = bag.epgRef
        epgServiceName = bag.epgName
        epgFocusedServiceRef = bag.epgFocusedRef
        epgTimeSec = bag.epgTimeSec
        profileEditArgs = savedStateHandle.get<Bundle>(PhoneNavSavedKeys.PROFILE_EDIT_ARGS)
        timerEditArgs = savedStateHandle.get<Bundle>(PhoneNavSavedKeys.TIMER_EDIT_ARGS)
        timerEditSession = readTimerEditSession()
    }

    fun hasSavedStartRoute(): Boolean = startRouteSaved

    fun setStartRoute(route: String) {
        startRouteValue = route
        startRouteSaved = true
        persistPlain()
    }

    private fun persistPlain() {
        PhoneNavStateBag(
            startRoute = if (startRouteSaved) startRouteValue else null,
            pickRequestCodes = resultRequestCodes.toList(),
            profileEditTag = profileEditTag,
            timerEditTag = timerEditTag,
            epgRef = epgServiceReference,
            epgName = epgServiceName,
            epgFocusedRef = epgFocusedServiceRef,
            epgTimeSec = epgTimeSec
        ).writePlain(plainAccess)
    }

    private fun putOrRemove(key: String, value: Any?) {
        if (value == null) {
            savedStateHandle.remove<Any>(key)
        } else {
            savedStateHandle[key] = value
        }
    }

    private fun persistProfileEditArgs() {
        putOrRemove(PhoneNavSavedKeys.PROFILE_EDIT_ARGS, profileEditArgs)
    }

    private fun persistTimerEditArgs() {
        putOrRemove(PhoneNavSavedKeys.TIMER_EDIT_ARGS, timerEditArgs)
    }

    private fun persistTimerEditSession() {
        val session = timerEditSession
        if (session == null) {
            removeTimerEditSessionKeys()
            return
        }
        val bundle = Bundle()
        session.writeTo(bundle)
        putOrRemove(TimerEditSession.STATE_TAG, bundle.getString(TimerEditSession.STATE_TAG))
        putOrRemove(
            TimerEditSession.STATE_REMOUNT,
            bundle.getInt(TimerEditSession.STATE_REMOUNT)
        )
        putOrRemove(
            TimerEditSession.STATE_TIMER,
            bundle.getSerializableCompat<Timer>(TimerEditSession.STATE_TIMER)
        )
        putOrRemove(
            TimerEditSession.STATE_TIMER_OLD,
            bundle.getSerializableCompat<Timer>(TimerEditSession.STATE_TIMER_OLD)
        )
        putOrRemove(
            TimerEditSession.STATE_TAGS,
            bundle.getStringArrayList(TimerEditSession.STATE_TAGS)
        )
        putOrRemove(
            TimerEditSession.STATE_CREATE,
            bundle.getBoolean(TimerEditSession.STATE_CREATE)
        )
        putOrRemove(
            TimerEditSession.STATE_CHECKED,
            bundle.getBooleanArray(TimerEditSession.STATE_CHECKED)
        )
    }

    private fun removeTimerEditSessionKeys() {
        savedStateHandle.remove<Any>(TimerEditSession.STATE_TAG)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_REMOUNT)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_TIMER)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_TIMER_OLD)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_TAGS)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_CREATE)
        savedStateHandle.remove<Any>(TimerEditSession.STATE_CHECKED)
    }

    private fun readTimerEditSession(): TimerEditSession? {
        val bundle = Bundle()
        savedStateHandle.get<String>(TimerEditSession.STATE_TAG)?.let { tag ->
            bundle.putString(TimerEditSession.STATE_TAG, tag)
        }
        if (savedStateHandle.contains(TimerEditSession.STATE_REMOUNT)) {
            bundle.putInt(
                TimerEditSession.STATE_REMOUNT,
                savedStateHandle.get<Int>(TimerEditSession.STATE_REMOUNT) ?: 0
            )
        }
        savedStateHandle.get<Timer>(TimerEditSession.STATE_TIMER)?.let { timer ->
            bundle.putSerializable(TimerEditSession.STATE_TIMER, timer)
        }
        savedStateHandle.get<Timer>(TimerEditSession.STATE_TIMER_OLD)?.let { timer ->
            bundle.putSerializable(TimerEditSession.STATE_TIMER_OLD, timer)
        }
        val tags = savedStateHandle.get<ArrayList<*>>(TimerEditSession.STATE_TAGS)
        if (tags != null) {
            val copy = ArrayList<String>(tags.size)
            for (item in tags) {
                if (item is String) {
                    copy.add(item)
                }
            }
            bundle.putStringArrayList(TimerEditSession.STATE_TAGS, copy)
        }
        if (savedStateHandle.contains(TimerEditSession.STATE_CREATE)) {
            bundle.putBoolean(
                TimerEditSession.STATE_CREATE,
                savedStateHandle.get<Boolean>(TimerEditSession.STATE_CREATE) == true
            )
        }
        savedStateHandle.get<BooleanArray>(TimerEditSession.STATE_CHECKED)?.let { checked ->
            bundle.putBooleanArray(TimerEditSession.STATE_CHECKED, checked)
        }
        return TimerEditSession.fromSavedState(bundle)
    }

    private fun updateEpgLeaf(
        serviceReference: String?,
        serviceName: String?,
        focusedServiceRef: String?,
        timeSec: Long?
    ) {
        epgServiceReference = serviceReference
        epgServiceName = serviceName
        epgFocusedServiceRef = focusedServiceRef
        epgTimeSec = timeSec
        persistPlain()
    }

    override fun startRoute(): String = startRouteValue

    override fun epgLeafArguments(): Bundle = Bundle().apply {
        putString(Event.KEY_SERVICE_REFERENCE, epgServiceReference)
        putString(Event.KEY_SERVICE_NAME, epgServiceName)
        putString(NavExtras.FOCUSED_SERVICE_REF, epgFocusedServiceRef)
        epgTimeSec?.let { putLong(NavExtras.EPG_TIME_SEC, it) }
    }

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

    override fun navigateToRoute(route: String): Boolean {
        clearResultRequestCodes()
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

    override fun navigateToEpg(
        serviceReference: String?,
        serviceName: String?,
        timeSec: Long?
    ): Boolean {
        val controller = navController
        updateEpgLeaf(serviceReference, serviceName, null, timeSec)
        if (controller == null) {
            pendingDrawerRoot = PhoneNavRoutes.EPG
            return true
        }
        val currentRoute = controller.currentDestination?.route
        if (currentRoute == PhoneNavRoutes.MULTI_EPG) {
            val previous = controller.previousBackStackEntry?.destination?.route
            if (previous == PhoneNavRoutes.EPG) {
                clearResultRequestCodes()
                controller.popBackStack()
                epgRemountState.value = epgRemountState.value + 1
                return true
            }
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            clearResultRequestCodes()
            controller.navigateDrawerRoot(PhoneNavRoutes.EPG)
        }
        epgRemountState.value = epgRemountState.value + 1
        return true
    }

    override fun navigateToDrawerEpg(): Boolean {
        val ctx = lifecycleOwner as Context
        val profile = DreamDroid.getCurrentProfile()
        val ref = profile.defaultBouquetTv
        val name = profile.defaultBouquetTvName
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (!DrawerEpgMode.isMulti(prefs)) {
            return navigateToEpg(ref, name)
        }
        val controller = navController
        updateEpgLeaf(ref, name, null, null)
        if (controller == null) {
            pendingDrawerRoot = PhoneNavRoutes.EPG
            pendingNestedMultiEpg = true
            return true
        }
        val currentRoute = controller.currentDestination?.route
        val previous = controller.previousBackStackEntry?.destination?.route
        if (DrawerEpgMode.isNestedOnListEpg(currentRoute, previous)) {
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            clearResultRequestCodes()
            controller.navigateDrawerRoot(PhoneNavRoutes.EPG)
        }
        return navigateToMultiEpg(ref, name)
    }

    override fun navigateToMultiEpg(
        serviceReference: String?,
        serviceName: String?,
        focusedServiceRef: String?,
        timeSec: Long?
    ): Boolean {
        val controller = navController
        updateEpgLeaf(serviceReference, serviceName, focusedServiceRef, timeSec)
        if (controller == null) {
            pendingNestedMultiEpg = true
            return true
        }
        if (controller.currentDestination?.route != PhoneNavRoutes.MULTI_EPG) {
            clearResultRequestCodes()
        }
        if (controller.currentDestination?.route == PhoneNavRoutes.MULTI_EPG) {
            epgRemountState.value = epgRemountState.value + 1
            return true
        }
        controller.navigateToMultiEpg()
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
        if (pendingNestedMultiEpg) {
            pendingNestedMultiEpg = false
            navigateToMultiEpg(
                epgServiceReference,
                epgServiceName,
                epgFocusedServiceRef,
                epgTimeSec
            )
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
        persistProfileEditArgs()
        persistPlain()
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
            persistTimerEditSession()
            return existing
        }
        val session = TimerEditSession.fromArgs(timerEditLeafArguments(), routeTag, remountEpoch)
        timerEditSession = session
        persistTimerEditSession()
        return session
    }

    override fun clearTimerEditSession() {
        timerEditSession = null
        persistTimerEditSession()
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
        persistTimerEditArgs()
        persistPlain()
        clearTimerEditSession()
        if (controller.currentDestination?.route == PhoneNavRoutes.TIMER_EDIT) {
            timerEditRemountState.value = timerEditRemountState.value + 1
            return true
        }
        controller.navigate(PhoneNavRoutes.TIMER_EDIT)
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
        if (route == null) return false
        return route == PhoneNavRoutes.PICK_SERVICE ||
            route == PhoneNavRoutes.PROFILE_EDIT ||
            route == PhoneNavRoutes.TIMER_EDIT ||
            route == PhoneNavRoutes.TIMER_SERVICE_PICK
    }

    private fun discardResultRequestCodeForCurrentRoute() {
        val route = navController?.currentDestination?.route
        if (isResultDestination(route) && resultRequestCodes.isNotEmpty()) {
            popResultRequestCode()
            if (route == PhoneNavRoutes.TIMER_EDIT) {
                clearTimerEditSession()
            }
        }
    }

    override fun navigateToTimerServicePick(): Boolean {
        val controller = navController ?: return false
        persistTimerEditSession()
        pushResultRequestCode(Statics.REQUEST_PICK_SERVICE)
        controller.navigate(PhoneNavRoutes.TIMER_SERVICE_PICK)
        return true
    }

    override fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = if (resultRequestCodes.isEmpty()) -1 else popResultRequestCode()
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

        override fun getLong(key: String): Long? = handle.get<Long>(key)

        override fun putLong(key: String, value: Long?) {
            if (value == null) {
                handle.remove<Long>(key)
            } else {
                handle[key] = value
            }
        }
    }
}
