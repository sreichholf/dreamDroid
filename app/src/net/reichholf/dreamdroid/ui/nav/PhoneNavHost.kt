package net.reichholf.dreamdroid.ui.nav

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.BackupFragment
import net.reichholf.dreamdroid.fragment.CurrentServiceFragment
import net.reichholf.dreamdroid.fragment.DeviceInfoFragment
import net.reichholf.dreamdroid.fragment.EpgBouquetFragment
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.ProfileListFragment
import net.reichholf.dreamdroid.fragment.ScreenShotFragment
import net.reichholf.dreamdroid.fragment.SignalFragment
import net.reichholf.dreamdroid.fragment.VirtualRemotePagerFragment
import net.reichholf.dreamdroid.fragment.ZapFragment
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Phone shell [NavHost]. Migrated drawer leaves through EPG and tablet Virtual Remote.
 * Hub (`ServiceListPager`) and phone-only remote activity still use NavigationHelper.
 */
@Composable
fun PhoneNavHost(
    hostFragment: PhoneNavHostFragment,
    navController: NavHostController = rememberNavController(),
    startDestination: String = hostFragment.startRoute(),
) {
    DisposableEffect(navController) {
        hostFragment.attachNavController(navController)
        onDispose { hostFragment.detachNavController(navController) }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(PhoneNavRoutes.DEVICE_INFO) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_device_info_slot,
                routeTag = PhoneNavRoutes.DEVICE_INFO,
                createFragment = { DeviceInfoFragment() },
            )
        }
        composable(PhoneNavRoutes.SIGNAL) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_signal_slot,
                routeTag = PhoneNavRoutes.SIGNAL,
                createFragment = { SignalFragment() },
            )
        }
        composable(PhoneNavRoutes.SCREENSHOT) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_screenshot_slot,
                routeTag = PhoneNavRoutes.SCREENSHOT,
                createFragment = { ScreenShotFragment() },
            )
        }
        composable(PhoneNavRoutes.CURRENT) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_current_slot,
                routeTag = PhoneNavRoutes.CURRENT,
                createFragment = { CurrentServiceFragment() },
            )
        }
        composable(PhoneNavRoutes.ZAP) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_zap_slot,
                routeTag = PhoneNavRoutes.ZAP,
                createFragment = { ZapFragment() },
            )
        }
        composable(PhoneNavRoutes.BACKUP) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_backup_slot,
                routeTag = PhoneNavRoutes.BACKUP,
                createFragment = { BackupFragment() },
            )
        }
        composable(PhoneNavRoutes.PROFILES) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_profiles_slot,
                routeTag = PhoneNavRoutes.PROFILES,
                createFragment = { ProfileListFragment() },
            )
        }
        composable(PhoneNavRoutes.EPG) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_epg_slot,
                routeTag = PhoneNavRoutes.EPG,
                createFragment = {
                    EpgBouquetFragment().apply {
                        arguments = hostFragment.epgLeafArguments()
                    }
                },
            )
        }
        composable(PhoneNavRoutes.REMOTE) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_remote_slot,
                routeTag = PhoneNavRoutes.REMOTE,
                createFragment = { VirtualRemotePagerFragment() },
            )
        }
    }
}

@Composable
private fun NestedFragmentDestination(
    hostFragment: Fragment,
    containerId: Int,
    routeTag: String,
    createFragment: () -> Fragment,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { container ->
            ensureNestedFragment(hostFragment, container.id, routeTag, createFragment)
        },
    )
}

/**
 * Mount [createFragment] under the host when missing. Never uses
 * `commitNowAllowingStateLoss`; if the child FM has already saved state, defer via
 * [android.view.View.post] until a safe window (e.g. after rotation restore).
 */
internal fun ensureNestedFragment(
    hostFragment: Fragment,
    containerId: Int,
    routeTag: String,
    createFragment: () -> Fragment,
) {
    if (!hostFragment.isAdded) return
    val fm = hostFragment.childFragmentManager
    if (fm.findFragmentById(containerId) != null) return
    if (!fm.isStateSaved) {
        commitNestedFragment(fm, containerId, routeTag, createFragment)
        return
    }
    hostFragment.view?.post {
        if (!hostFragment.isAdded) return@post
        val childFm = hostFragment.childFragmentManager
        if (childFm.findFragmentById(containerId) != null || childFm.isStateSaved) return@post
        commitNestedFragment(childFm, containerId, routeTag, createFragment)
    }
}

private fun commitNestedFragment(
    fm: FragmentManager,
    containerId: Int,
    routeTag: String,
    createFragment: () -> Fragment,
) {
    fm.beginTransaction()
        .replace(containerId, createFragment(), routeTag)
        .commitNow()
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

fun ComposeView.bindPhoneNavHost(hostFragment: PhoneNavHostFragment) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(hostFragment = hostFragment)
        }
    }
}
