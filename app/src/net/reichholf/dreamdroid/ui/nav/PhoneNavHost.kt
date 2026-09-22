package net.reichholf.dreamdroid.ui.nav

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

private val SleepTimerNavArgsSaver = listSaver<SleepTimerNavArgs, Any>(
    save = { listOf(it.minutes, it.enabled, it.action) },
    restore = {
        SleepTimerNavArgs(
            minutes = it[0] as Int,
            enabled = it[1] as Boolean,
            action = it[2] as String
        )
    }
)

/**
 * Snapshot sleep-timer args once per dialog entry. [PhoneNavHandle.consumeSleepTimerArgs]
 * nulls pending args, so calling it on every composition would reset to
 * [SleepTimerNavArgs.defaults].
 */
@Composable
fun rememberSleepTimerNavArgs(consume: () -> SleepTimerNavArgs): SleepTimerNavArgs =
    rememberSaveable(saver = SleepTimerNavArgsSaver) { consume() }

/**
 * Phone shell [NavHost]. Drawer leaves through hub + settings; Backup is nested from Settings.
 * Nested service EPG, EPG search, bouquet pick, and MultiEPG are nested
 * destinations (back returns to the leaf that opened them).
 */
@Composable
fun PhoneNavHost(
    handle: PhoneNavHandle,
    navController: NavHostController = rememberNavController(),
    startDestination: String = handle.startRoute()
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
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(PhoneNavRoutes.DEVICE_INFO) {
            DeviceInfoDestination()
        }
        composable(PhoneNavRoutes.SIGNAL) {
            SignalDestination(handle = handle)
        }
        composable(PhoneNavRoutes.SCREENSHOT) {
            ScreenshotDestination(handle = handle)
        }
        composable(PhoneNavRoutes.CURRENT) {
            CurrentServiceDestination(handle = handle)
        }
        composable(PhoneNavRoutes.ZAP) {
            ZapDestination(handle = handle)
        }
        composable(PhoneNavRoutes.BACKUP) {
            BackupDestination()
        }
        composable(PhoneNavRoutes.PROFILES) {
            ProfilesDestination(handle = handle)
        }
        composable(PhoneNavRoutes.EPG) {
            val remount by handle.epgRemountFlow().collectAsState()
            key(remount) {
                EpgBouquetDestination(handle = handle, remountEpoch = remount)
            }
        }
        composable(PhoneNavRoutes.MULTI_EPG) {
            val remount by handle.epgRemountFlow().collectAsState()
            key(remount) {
                MultiEpgDestination(handle = handle, remountEpoch = remount)
            }
        }
        composable(PhoneNavRoutes.REMOTE) {
            VirtualRemoteDestination(handle = handle)
        }
        composable(PhoneNavRoutes.SETTINGS) {
            SettingsDestination(handle = handle)
        }
        composable(PhoneNavRoutes.HUB) {
            HubDestination(handle = handle)
        }
        composable(PhoneNavRoutes.TOOLS) {
            ToolsHubDestination(handle = handle)
        }
        composable(PhoneNavRoutes.PROFILE_CHECK) {
            ProfileCheckDestination(handle = handle)
        }
        composable(
            route = PhoneNavRoutes.SERVICE_EPG,
            arguments = listOf(
                navArgument(PhoneNavRoutes.ARG_SERVICE_REF) { type = NavType.StringType },
                navArgument(PhoneNavRoutes.ARG_SERVICE_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val serviceRef = entry.arguments?.getString(PhoneNavRoutes.ARG_SERVICE_REF).orEmpty()
            val serviceName = entry.arguments?.getString(PhoneNavRoutes.ARG_SERVICE_NAME).orEmpty()
            ServiceEpgDestination(
                handle = handle,
                serviceRef = serviceRef,
                serviceName = serviceName
            )
        }
        composable(
            route = PhoneNavRoutes.EPG_SEARCH,
            arguments = listOf(
                navArgument(PhoneNavRoutes.ARG_QUERY) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val query = entry.arguments?.getString(PhoneNavRoutes.ARG_QUERY).orEmpty()
            val remount by handle.epgSearchRemountFlow().collectAsState()
            key(query, remount) {
                EpgSearchDestination(
                    handle = handle,
                    query = query,
                    remountEpoch = remount
                )
            }
        }
        composable(PhoneNavRoutes.PICK_SERVICE) {
            PickServiceDestination(handle = handle)
        }
        composable(PhoneNavRoutes.PROFILE_EDIT) {
            val remount by handle.profileEditRemountFlow().collectAsState()
            key(handle.profileEditRouteTag(), remount) {
                ProfileEditDestination(handle = handle)
            }
        }
        composable(PhoneNavRoutes.TIMER_EDIT) {
            val remount by handle.timerEditRemountFlow().collectAsState()
            key(handle.timerEditRouteTag(), remount) {
                TimerEditDestination(handle = handle)
            }
        }
        composable(PhoneNavRoutes.TIMER_SERVICE_PICK) {
            TimerServicePickDestination(handle = handle)
        }
        dialog(PhoneNavRoutes.ABOUT) {
            AboutDialog(onDismiss = { navController.popBackStack() })
        }
        dialog(PhoneNavRoutes.POWER) {
            val activity = LocalContext.current as? MainActivity
            PowerStateDialog(
                onDismiss = { navController.popBackStack() },
                onChoice = { action -> activity?.onDrawerPowerChoice(action) }
            )
        }
        dialog(PhoneNavRoutes.SEND_MESSAGE) {
            val activity = LocalContext.current as? MainActivity
            SendMessageDialog(
                onDismiss = { navController.popBackStack() },
                onSend = { text, type, timeout ->
                    activity?.onSendMessage(text, type, timeout)
                }
            )
        }
        dialog(PhoneNavRoutes.SLEEP_TIMER) {
            val activity = LocalContext.current as? MainActivity
            val args = rememberSleepTimerNavArgs {
                handle.consumeSleepTimerArgs()
            }
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
        dialog(
            PhoneNavRoutes.CHANGELOG,
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

/** Push About as a Navigation `dialog` (back / dismiss pops it). */
fun NavHostController.navigateToAbout() {
    if (currentDestination?.route == PhoneNavRoutes.ABOUT) return
    navigate(PhoneNavRoutes.ABOUT)
}

/** Push drawer Power as a Navigation `dialog`. */
fun NavHostController.navigateToPower() {
    if (currentDestination?.route == PhoneNavRoutes.POWER) return
    navigate(PhoneNavRoutes.POWER)
}

/** Push drawer Send Message as a Navigation `dialog`. */
fun NavHostController.navigateToSendMessage() {
    if (currentDestination?.route == PhoneNavRoutes.SEND_MESSAGE) return
    navigate(PhoneNavRoutes.SEND_MESSAGE)
}

/** Push drawer Sleep Timer as a Navigation `dialog` (args via host). */
fun NavHostController.navigateToSleepTimer() {
    if (currentDestination?.route == PhoneNavRoutes.SLEEP_TIMER) return
    navigate(PhoneNavRoutes.SLEEP_TIMER)
}

/** Push Changelog as a Navigation `dialog` that hosts [ChangelogDialog]'s sheet. */
fun NavHostController.navigateToChangelog() {
    if (currentDestination?.route == PhoneNavRoutes.CHANGELOG) return
    navigate(PhoneNavRoutes.CHANGELOG)
}

/** Push the full-screen profile-check gate (checking / failed) onto the back stack. */
fun NavHostController.navigateToProfileCheck() {
    if (currentDestination?.route == PhoneNavRoutes.PROFILE_CHECK) return
    navigate(PhoneNavRoutes.PROFILE_CHECK) {
        launchSingleTop = true
    }
}

/**
 * Open a drawer root above [PhoneNavRoutes.PROFILE_CHECK] without popping the gate
 * (e.g. Profiles from a failed check so Back can return to Recheck).
 */
fun NavHostController.navigateAboveProfileCheck(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Leave the profile-check gate for [route], removing [PhoneNavRoutes.PROFILE_CHECK]
 * from the back stack so Back from the service list does not return to the check.
 */
fun NavHostController.navigateReplacingProfileCheck(route: String) {
    navigate(route) {
        popUpTo(PhoneNavRoutes.PROFILE_CHECK) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

/** Drawer-style top-level navigate: single-top + save/restore under the start destination. */
fun NavHostController.navigateDrawerRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Nested service EPG: push onto the NavHost back stack (back returns to hub). */
fun NavHostController.navigateToServiceEpg(serviceRef: String, serviceName: String?) {
    val route = "service_epg/${Uri.encode(serviceRef)}" +
        "?serviceName=${Uri.encode(serviceName.orEmpty())}"
    navigate(route)
}

/** Nested MultiEPG: push onto the back stack (back returns to hub or list EPG). */
fun NavHostController.navigateToMultiEpg() {
    navigate(PhoneNavRoutes.MULTI_EPG) {
        launchSingleTop = true
    }
}

/** Nested EPG search: push onto the NavHost back stack (singleTop avoids duplicate same query). */
fun NavHostController.navigateToEpgSearch(query: String) {
    navigate(PhoneNavRoutes.epgSearchRoute(query)) {
        launchSingleTop = true
    }
}

/** Nested Backup from Settings. Back returns to Settings. */
fun NavHostController.navigateToBackup() {
    if (currentDestination?.route == PhoneNavRoutes.BACKUP) return
    navigate(PhoneNavRoutes.BACKUP) { launchSingleTop = true }
}

/** Drawer Settings: land on Settings, not a nested Backup restored on top. */
fun NavHostController.navigateDrawerSettings() {
    if (currentDestination?.route == PhoneNavRoutes.BACKUP) {
        if (popBackStack(PhoneNavRoutes.SETTINGS, false)) return
    }
    navigateDrawerRoot(PhoneNavRoutes.SETTINGS)
    if (currentDestination?.route == PhoneNavRoutes.BACKUP) {
        popBackStack(PhoneNavRoutes.SETTINGS, false)
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
