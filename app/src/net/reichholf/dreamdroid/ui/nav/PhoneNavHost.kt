package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.ui.about.AboutDialog
import net.reichholf.dreamdroid.ui.backup.BackupDestination
import net.reichholf.dreamdroid.ui.current.CurrentServiceDestination
import net.reichholf.dreamdroid.ui.device.DeviceInfoDestination
import net.reichholf.dreamdroid.ui.dialogs.ChangelogDialog
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.ExplainAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.PowerStateDialog
import net.reichholf.dreamdroid.ui.dialogs.SendMessageDialog
import net.reichholf.dreamdroid.ui.dialogs.SleepTimerDialog
import net.reichholf.dreamdroid.ui.dialogs.defaultSleepTimerAction
import net.reichholf.dreamdroid.ui.epg.EpgBouquetDestination
import net.reichholf.dreamdroid.ui.epg.EpgSearchDestination
import net.reichholf.dreamdroid.ui.epg.ServiceEpgDestination
import net.reichholf.dreamdroid.ui.multiepg.MultiEpgDestination
import net.reichholf.dreamdroid.ui.pick.PickServiceDestination
import net.reichholf.dreamdroid.ui.pick.TimerServicePickDestination
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckDestination
import net.reichholf.dreamdroid.ui.profiles.ProfileEditDestination
import net.reichholf.dreamdroid.ui.profiles.ProfilesDestination
import net.reichholf.dreamdroid.ui.remote.VirtualRemoteDestination
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.services.HubDestination
import net.reichholf.dreamdroid.ui.settings.SettingsDestination
import net.reichholf.dreamdroid.ui.signal.SignalDestination
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.timers.TimerEditDestination
import net.reichholf.dreamdroid.ui.tools.ToolsHubDestination
import net.reichholf.dreamdroid.ui.zap.ZapDestination

/**
 * [rememberNavController], and on the first launch after type-safe routes, drop
 * a back stack saved with the old string patterns so [NavHost] can start cleanly.
 */
@Composable
fun rememberPhoneNavController(handle: PhoneNavHandle): NavHostController {
    val controller = rememberNavController()
    val state = handle as? PhoneNavHostState
    if (state != null && !state.hasNavSchema()) {
        controller.restoreState(Bundle())
    }
    SideEffect { state?.markNavSchema() }
    return controller
}

/**
 * Phone shell [NavHost]. Drawer leaves through hub + settings; Backup is nested from Settings.
 * Nested service EPG, EPG search, bouquet pick, and MultiEPG are nested
 * destinations (back returns to the leaf that opened them).
 */
@Composable
fun PhoneNavHost(
    handle: PhoneNavHandle,
    navController: NavHostController = rememberPhoneNavController(handle),
    startDestination: Any = startDestinationForSavedId(handle.startRoute(), DeviceInfo)
) {
    DisposableEffect(navController) {
        handle.attachNavController(navController)
        onDispose { handle.detachNavController(navController) }
    }
    // Shell destination bar lives for the NavHost lifetime; hubs only publish Snapshot state.
    ProvideShellDestinationBar {
        val controller = LocalShellDestinationBarController.current
        DisposableEffect(handle, controller) {
            val state = handle as? PhoneNavHostState
            state?.shellDestinationBarController = controller
            onDispose {
                if (state != null && state.shellDestinationBarController === controller) {
                    state.shellDestinationBarController = null
                }
            }
        }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val shellBarVisible = PhoneNavRoutes.showsShellDestinationBar(
            backStackEntry?.destination?.route
        )
        PhoneNavHostGraph(
            handle = handle,
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .phoneNavDestinationViewport(shellBarVisible)
        )
        val leaveConfirm by handle.leaveConfirmRequestedFlow().collectAsState()
        if (leaveConfirm) {
            val context = LocalContext.current
            ConfirmAlertDialog(
                title = stringResource(R.string.leave_confirm),
                message = stringResource(R.string.leave_confirm_long),
                onDismiss = { handle.clearLeaveConfirm() },
                onConfirm = { (context as? Activity)?.finish() }
            )
        }
        val needsReceiver by handle.needsReceiverRequestedFlow().collectAsState()
        if (needsReceiver) {
            ExplainAlertDialog(
                title = stringResource(R.string.session_needs_receiver),
                message = stringResource(R.string.session_needs_receiver_long),
                onDismiss = { handle.clearNeedsReceiver() }
            )
        }
    }
}

@Composable
private fun PhoneNavHostGraph(
    handle: PhoneNavHandle,
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<DeviceInfo> {
            DeviceInfoDestination()
        }
        composable<Signal> {
            SignalDestination(handle = handle)
        }
        composable<Screenshot> {
            ScreenshotDestination(handle = handle)
        }
        composable<Current> {
            CurrentServiceDestination(handle = handle)
        }
        composable<Zap> {
            ZapDestination(handle = handle)
        }
        composable<Backup> {
            BackupDestination()
        }
        composable<Profiles> {
            ProfilesDestination(handle = handle)
        }
        composable<Epg> { entry ->
            val route = entry.toRoute<Epg>()
            val remount by handle.epgRemountFlow().collectAsState()
            key(remount) {
                EpgBouquetDestination(
                    handle = handle,
                    route = route,
                    remountEpoch = remount
                )
            }
        }
        composable<MultiEpg> { entry ->
            val route = entry.toRoute<MultiEpg>()
            val remount by handle.epgRemountFlow().collectAsState()
            key(remount) {
                MultiEpgDestination(
                    handle = handle,
                    route = route,
                    remountEpoch = remount
                )
            }
        }
        composable<Remote> {
            VirtualRemoteDestination(handle = handle)
        }
        composable<Settings> {
            SettingsDestination(handle = handle)
        }
        composable<Hub> {
            HubDestination(handle = handle)
        }
        composable<Tools> {
            ToolsHubDestination(handle = handle)
        }
        composable<ProfileCheck> {
            ProfileCheckDestination(handle = handle)
        }
        composable<ServiceEpg> {
            ServiceEpgDestination(handle = handle)
        }
        composable<EpgSearch> { entry ->
            val query = entry.toRoute<EpgSearch>().query
            val remount by handle.epgSearchRemountFlow().collectAsState()
            key(query, remount) {
                EpgSearchDestination(
                    handle = handle,
                    query = query,
                    remountEpoch = remount
                )
            }
        }
        composable<PickService> {
            PickServiceDestination(handle = handle)
        }
        composable<ProfileEdit> { entry ->
            val route = entry.toRoute<ProfileEdit>()
            val remount by handle.profileEditRemountFlow().collectAsState()
            key(route.tag(), remount) {
                ProfileEditDestination(
                    handle = handle,
                    route = route,
                    remountEpoch = remount
                )
            }
        }
        composable<TimerEdit> { entry ->
            val route = entry.toRoute<TimerEdit>()
            val remount by handle.timerEditRemountFlow().collectAsState()
            key(route.tag(), remount) {
                TimerEditDestination(
                    handle = handle,
                    route = route,
                    remountEpoch = remount
                )
            }
        }
        composable<TimerServicePick> {
            TimerServicePickDestination(handle = handle)
        }
        dialog<About> {
            AboutDialog(onDismiss = { navController.popBackStack() })
        }
        dialog<Power> {
            val activity = LocalActivity.current as? MainActivity
            PowerStateDialog(
                onDismiss = { navController.popBackStack() },
                onChoice = { action -> activity?.onDrawerPowerChoice(action) }
            )
        }
        dialog<SendMessage> {
            val activity = LocalActivity.current as? MainActivity
            SendMessageDialog(
                onDismiss = { navController.popBackStack() },
                onSend = { text, type, timeout ->
                    activity?.onSendMessage(text, type, timeout)
                }
            )
        }
        dialog<SleepTimerRoute> { entry ->
            val activity = LocalActivity.current as? MainActivity
            val args = entry.toRoute<SleepTimerRoute>()
            SleepTimerDialog(
                initialMinutes = args.minutes,
                initialEnabled = args.enabled,
                initialAction = args.action.ifEmpty { defaultSleepTimerAction() },
                onDismiss = { navController.popBackStack() },
                onSave = { time, action, enabled ->
                    activity?.onSetSleepTimer(time, action, enabled)
                }
            )
        }
        dialog<Changelog>(
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false
            )
        ) {
            ChangelogDialog(onDismiss = { navController.popBackStack() })
        }
    }
}

/** Pop [route]'s destination, then push [route], so new arguments replace the entry. */
inline fun <reified T : Any> NavHostController.replaceRoute(route: T) {
    navigate(route) {
        popUpTo<T> { inclusive = true }
        launchSingleTop = true
    }
}

/** Push About as a Navigation `dialog` (back / dismiss pops it). */
fun NavHostController.navigateToAbout() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.ABOUT) return
    navigate(About)
}

/** Push drawer Power as a Navigation `dialog`. */
fun NavHostController.navigateToPower() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.POWER) return
    navigate(Power)
}

/** Push drawer Send Message as a Navigation `dialog`. */
fun NavHostController.navigateToSendMessage() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.SEND_MESSAGE) return
    navigate(SendMessage)
}

/** Push drawer Sleep Timer as a Navigation `dialog`. Args live on [route]. */
fun NavHostController.navigateToSleepTimer(route: SleepTimerRoute = SleepTimerRoute()) {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.SLEEP_TIMER) return
    navigate(route)
}

/** Push Changelog as a Navigation `dialog` that hosts [ChangelogDialog]'s sheet. */
fun NavHostController.navigateToChangelog() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.CHANGELOG) return
    navigate(Changelog)
}

/** Push the full-screen profile-check gate (checking / failed) onto the back stack. */
fun NavHostController.navigateToProfileCheck() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.PROFILE_CHECK) return
    navigate(ProfileCheck) {
        launchSingleTop = true
    }
}

/**
 * Open a drawer root above [ProfileCheck] without popping the gate
 * (e.g. Profiles from a failed check so Back can return to Recheck).
 */
fun NavHostController.navigateAboveProfileCheck(route: Any) {
    val destination = normalizeRoute(route)
    if (routeKey(currentDestination?.route) == routeId(destination)) {
        return
    }
    navigate(destination) {
        launchSingleTop = true
    }
}

/**
 * Leave the profile-check gate for [route], removing [ProfileCheck]
 * from the back stack so Back from the service list does not return to the check.
 */
fun NavHostController.navigateReplacingProfileCheck(route: Any) {
    navigate(normalizeRoute(route)) {
        popUpTo<ProfileCheck> { inclusive = true }
        launchSingleTop = true
    }
}

/** Drawer-style top-level navigate: single-top + save/restore under the start destination. */
fun NavHostController.navigateDrawerRoot(route: Any) {
    val destination = normalizeRoute(route)
    val keepSavedState = destination !is Epg && destination !is MultiEpg
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = keepSavedState
    }
}

/** Nested service EPG: push onto the NavHost back stack (back returns to hub). */
fun NavHostController.navigateToServiceEpg(serviceRef: String, serviceName: String?) {
    navigate(ServiceEpg(serviceRef = serviceRef, serviceName = serviceName.orEmpty()))
}

/** Nested EPG search: push onto the NavHost back stack (singleTop avoids duplicate same query). */
fun NavHostController.navigateToEpgSearch(query: String) {
    navigate(EpgSearch(query = query)) {
        launchSingleTop = true
    }
}

/** Nested Backup from Settings. Back returns to Settings. */
fun NavHostController.navigateToBackup() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.BACKUP) return
    navigate(Backup) { launchSingleTop = true }
}

/** Drawer Settings: land on Settings, not a nested Backup restored on top. */
fun NavHostController.navigateDrawerSettings() {
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.BACKUP) {
        if (popBackStack<Settings>(inclusive = false)) return
    }
    navigateDrawerRoot(Settings)
    if (routeKey(currentDestination?.route) == PhoneNavRoutes.BACKUP) {
        popBackStack<Settings>(inclusive = false)
    }
}

fun ComposeView.bindPhoneNavHost(handle: PhoneNavHandle) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(handle = handle)
        }
    }
}
