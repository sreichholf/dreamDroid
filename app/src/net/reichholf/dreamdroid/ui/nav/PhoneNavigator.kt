package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.StateFlow
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * The phone shell's [PhoneNavHandle], owned by the activity and created again with it. It
 * drives the attached NavHostController and keeps what must outlive the activity (saved
 * routes, queued navigations, the dialogs' flags) in the activity-scoped [state].
 */
class PhoneNavigator(
    private val state: PhoneNavHostState,
    override val lifecycleOwner: LifecycleOwner,
    private val highlighter: DrawerRouteHighlighter?,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val sessions: SessionConnectionHolder
) : PhoneNavHandle {
    private var navController: NavHostController? = null
    internal var shellDestinationBarController: ShellDestinationBarController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending: PendingNavigations
        get() = state.pending

    override var composeActivityResultListener: PhoneNavHandle.ActivityResultListener? = null

    override val navUiState: StateFlow<PhoneNavUiState>
        get() = state.uiState

    override fun onActiveProfileChanged() {
        state.remountEpg()
    }

    fun hasSavedStartRoute(): Boolean = state.hasSavedStartRoute()

    fun setStartRoute(route: String) {
        state.setStartRoute(route)
    }

    /** False until this process has accepted the type-safe route schema. */
    fun hasNavSchema(): Boolean = state.hasNavSchema()

    fun markNavSchema() {
        state.markNavSchema()
    }

    override fun startRoute(): String = state.startRoute

    override fun attachNavController(controller: NavHostController) {
        navController = controller
        controller.addOnDestinationChangedListener { _, dest, _ ->
            val previous = controller.previousBackStackEntry?.destination?.route
            highlighter?.highlightDrawerForRoute(dest.route, previous)
            val activity = lifecycleOwner as? Activity
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
        state.clearResultRequestCodes()
        val destination = normalizeRoute(route)
        val controller = navController
        if (controller == null) {
            pending.drawerRoot = destination
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
            pending.backup = true
            return true
        }
        controller.navigateToBackup()
        return true
    }

    override fun navigateToAbout(): Boolean {
        val controller = navController
        if (controller == null) {
            pending.about = true
            return true
        }
        controller.navigateToAbout()
        return true
    }

    override fun navigateToPower(): Boolean {
        val controller = navController
        if (controller == null) {
            pending.power = true
            return true
        }
        controller.navigateToPower()
        return true
    }

    override fun navigateToSendMessage(): Boolean {
        val controller = navController
        if (controller == null) {
            pending.sendMessage = true
            return true
        }
        controller.navigateToSendMessage()
        return true
    }

    override fun navigateToSleepTimer(timer: SleepTimer): Boolean {
        pending.sleepTimer = timer.toSleepTimerRoute()
        val controller = navController
        if (controller == null) {
            pending.openSleepTimer = true
            return true
        }
        controller.navigateToSleepTimer(pending.sleepTimer ?: SleepTimerRoute())
        pending.sleepTimer = null
        return true
    }

    override fun queueSleepTimer(timer: SleepTimer) {
        pending.sleepTimer = timer.toSleepTimerRoute()
        pending.openSleepTimer = true
        flushPendingNavigations()
    }

    override fun navigateToChangelog(): Boolean {
        val controller = navController
        if (controller == null) {
            pending.changelog = true
            return true
        }
        controller.navigateToChangelog()
        return true
    }

    override fun queueChangelog() {
        pending.changelog = true
        flushPendingNavigations()
    }

    override fun connectionStatusFlow(): StateFlow<ConnectionStatus> = sessions.status

    override fun requestLeaveConfirm() {
        state.requestLeaveConfirm()
    }

    override fun clearLeaveConfirm() {
        state.clearLeaveConfirm()
    }

    override fun requestNeedsReceiver() {
        state.requestNeedsReceiver()
    }

    override fun clearNeedsReceiver() {
        state.clearNeedsReceiver()
    }

    override fun isOnProfileCheckRoute(): Boolean =
        routeKey(navController?.currentDestination?.route) == PhoneNavRoutes.PROFILE_CHECK

    override fun navigateToProfileCheck(): Boolean {
        val controller = navController
        if (controller == null) {
            pending.profileCheck = true
            return true
        }
        controller.navigateToProfileCheck()
        return true
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
            pending.drawerRoot = route
            return true
        }
        val currentRoute = routeKey(controller.currentDestination?.route)
        val previous = routeKey(controller.previousBackStackEntry?.destination?.route)
        if (currentRoute == PhoneNavRoutes.MULTI_EPG && previous == PhoneNavRoutes.EPG) {
            state.clearResultRequestCodes()
            controller.popBackStack()
            controller.replaceRoute<Epg>(route)
            state.remountEpg()
            return true
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            state.clearResultRequestCodes()
            controller.navigateDrawerRoot(route)
        } else {
            controller.replaceRoute<Epg>(route)
        }
        state.remountEpg()
        return true
    }

    override fun navigateToDrawerEpg(): Boolean {
        val profile = profiles.requireCurrent()
        val ref = profile.defaultBouquetTv
        val name = profile.defaultBouquetTvName
        if (!settings.drawerEpgMulti) {
            return navigateToEpg(ref, name)
        }
        val listRoute = Epg(serviceRef = ref.orEmpty(), serviceName = name.orEmpty())
        val controller = navController
        if (controller == null) {
            pending.drawerRoot = listRoute
            pending.multiEpg = MultiEpg(serviceRef = ref.orEmpty(), serviceName = name.orEmpty())
            return true
        }
        val currentRoute = routeKey(controller.currentDestination?.route)
        val previous = routeKey(controller.previousBackStackEntry?.destination?.route)
        if (DrawerEpgMode.isNestedOnListEpg(currentRoute, previous)) {
            state.remountEpg()
            return true
        }
        if (currentRoute != PhoneNavRoutes.EPG) {
            state.clearResultRequestCodes()
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
            pending.multiEpg = route
            return true
        }
        val onMulti = routeKey(controller.currentDestination?.route) == PhoneNavRoutes.MULTI_EPG
        if (!onMulti) {
            state.clearResultRequestCodes()
            controller.navigate(route) { launchSingleTop = true }
        } else {
            controller.replaceRoute<MultiEpg>(route)
        }
        state.remountEpg()
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
            pending.epgSearchQuery = q
            return true
        }
        val route = EpgSearch(query = q)
        val onSearch = routeKey(controller.currentDestination?.route) == PhoneNavRoutes.EPG_SEARCH
        if (onSearch && q.isEmpty()) {
            return true
        }
        if (onSearch) {
            controller.replaceRoute(route)
            state.remountEpgSearch()
            return true
        }
        controller.navigateToEpgSearch(q)
        return true
    }

    override fun navigateToPickBouquet(requestCode: Int): Boolean {
        val controller = navController ?: return false
        state.pushResultRequestCode(requestCode)
        controller.navigate(PickService)
        return true
    }

    override fun queueProfileEdit(profile: Profile?) {
        pending.profileEditRequested = true
        pending.profileEdit = profile
        flushPendingNavigations()
    }

    override fun queueTimerEdit(timer: Timer, create: Boolean) {
        pending.timerEdit = timer
        pending.timerCreate = create
        flushPendingNavigations()
    }

    override fun queueEpgSearch(query: String) {
        pending.epgSearchQuery = query
        flushPendingNavigations()
    }

    private fun flushPendingNavigations() {
        if (navController == null) return
        val drawerRoot = pending.drawerRoot
        if (drawerRoot != null) {
            pending.drawerRoot = null
            navigateToRoute(drawerRoot)
        }
        if (pending.profileEditRequested) {
            pending.profileEditRequested = false
            val profile = pending.profileEdit
            pending.profileEdit = null
            navigateToProfileEdit(profile)
        }
        val timer = pending.timerEdit
        if (timer != null) {
            pending.timerEdit = null
            val create = pending.timerCreate
            pending.timerCreate = false
            navigateToTimerEdit(timer, create)
        }
        val searchQuery = pending.epgSearchQuery
        if (searchQuery != null) {
            pending.epgSearchQuery = null
            navigateToEpgSearch(searchQuery)
        }
        if (pending.backup) {
            pending.backup = false
            navController?.navigateToBackup()
        }
        if (pending.about) {
            pending.about = false
            navController?.navigateToAbout()
        }
        if (pending.power) {
            pending.power = false
            navController?.navigateToPower()
        }
        if (pending.sendMessage) {
            pending.sendMessage = false
            navController?.navigateToSendMessage()
        }
        if (pending.openSleepTimer) {
            pending.openSleepTimer = false
            val route = pending.sleepTimer ?: SleepTimerRoute()
            pending.sleepTimer = null
            navController?.navigateToSleepTimer(route)
        }
        if (pending.changelog) {
            pending.changelog = false
            navController?.navigateToChangelog()
        }
        if (pending.profileCheck) {
            pending.profileCheck = false
            navController?.navigateToProfileCheck()
        }
        val multi = pending.multiEpg
        if (multi != null) {
            pending.multiEpg = null
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
        state.pushResultRequestCode(Statics.REQUEST_EDIT_PROFILE)
        val route = profile.toProfileEditRoute()
        if (routeKey(controller.currentDestination?.route) == PhoneNavRoutes.PROFILE_EDIT) {
            controller.replaceRoute(route)
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
        if (isResultDestination(controller.currentDestination?.route)) {
            state.popResultRequestCode()
        }
        return controller.popBackStack()
    }

    override fun navigateToBouquetContent(
        bouquetRef: String,
        bouquetName: String,
        mode: BouquetMode
    ): Boolean {
        val controller = navController ?: return false
        controller.navigate(BouquetContent(bouquetRef, bouquetName, mode.name)) {
            launchSingleTop = true
        }
        return true
    }

    override fun navigateToBouquetAddServices(bouquetRef: String, mode: BouquetMode): Boolean {
        val controller = navController ?: return false
        controller.navigate(BouquetAddServices(bouquetRef, mode.name)) {
            launchSingleTop = true
        }
        return true
    }

    override fun navigateToTimerEdit(timer: Timer, create: Boolean): Boolean {
        val controller = navController
        if (controller == null) {
            queueTimerEdit(timer, create)
            return true
        }
        state.pushResultRequestCode(Statics.REQUEST_EDIT_TIMER)
        val route = TimerEdit.from(timer, create)
        if (routeKey(controller.currentDestination?.route) == PhoneNavRoutes.TIMER_EDIT) {
            controller.replaceRoute(route)
            return true
        }
        controller.navigate(route)
        return true
    }

    private fun isResultDestination(route: String?): Boolean {
        val key = routeKey(route) ?: return false
        return key == PhoneNavRoutes.PICK_SERVICE ||
            key == PhoneNavRoutes.PROFILE_EDIT ||
            key == PhoneNavRoutes.TIMER_EDIT ||
            key == PhoneNavRoutes.TIMER_SERVICE_PICK
    }

    override fun navigateToTimerServicePick(): Boolean {
        val controller = navController ?: return false
        state.pushResultRequestCode(Statics.REQUEST_PICK_SERVICE)
        controller.navigate(TimerServicePick)
        return true
    }

    override fun deliverPickResult(resultCode: Int, data: Intent?) {
        val controller = navController ?: return
        val code = state.popResultRequestCode() ?: -1
        if (!controller.popBackStack()) return
        mainHandler.post {
            deliverComposeActivityResult(code, resultCode, data)
        }
    }

    override fun dispatchPendingComposeActivityResult() {
        val listener = composeActivityResultListener ?: return
        val (held, data) = state.takeHeldActivityResult() ?: return
        listener.onActivityResult(held.requestCode, held.resultCode, data)
    }

    override fun onHostActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        deliverComposeActivityResult(requestCode, resultCode, data)
    }

    /**
     * A result that arrives while no destination listens is held until the next one
     * registers: timer-edit save pops to the hub, then posts the result, and the post can
     * run before the hub's listener is back.
     */
    private fun deliverComposeActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode < 0) {
            return
        }
        val listener = composeActivityResultListener
        if (listener == null) {
            state.holdActivityResult(PendingComposeActivityResult(requestCode, resultCode), data)
            return
        }
        state.takeHeldActivityResult()
        listener.onActivityResult(requestCode, resultCode, data)
    }
}
