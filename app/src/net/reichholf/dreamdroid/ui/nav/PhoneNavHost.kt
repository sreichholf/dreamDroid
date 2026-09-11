package net.reichholf.dreamdroid.ui.nav

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment
import net.reichholf.dreamdroid.fragment.EpgBouquetFragment
import net.reichholf.dreamdroid.ui.backup.BackupDestination
import net.reichholf.dreamdroid.ui.current.CurrentServiceDestination
import net.reichholf.dreamdroid.ui.device.DeviceInfoDestination
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.signal.SignalDestination
import net.reichholf.dreamdroid.fragment.EpgSearchFragment
import net.reichholf.dreamdroid.fragment.MyPreferenceFragment
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.PickServiceFragment
import net.reichholf.dreamdroid.fragment.ProfileEditFragment
import net.reichholf.dreamdroid.fragment.TimerEditFragment
import net.reichholf.dreamdroid.fragment.TimerServicePickFragment
import net.reichholf.dreamdroid.fragment.ProfileListFragment
import net.reichholf.dreamdroid.fragment.ServiceEpgListFragment
import net.reichholf.dreamdroid.fragment.ServiceListPager
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.ui.remote.VirtualRemoteDestination
import net.reichholf.dreamdroid.ui.zap.ZapDestination
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Phone shell [NavHost]. Drawer leaves through hub + settings; Backup is nested from Settings.
 * Nested service EPG, EPG search, and bouquet pick are the 2.1f beachheads.
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
            DeviceInfoDestination()
        }
        composable(PhoneNavRoutes.SIGNAL) {
            SignalDestination()
        }
        composable(PhoneNavRoutes.SCREENSHOT) {
            ScreenshotDestination()
        }
        composable(PhoneNavRoutes.CURRENT) {
            CurrentServiceDestination(hostFragment = hostFragment)
        }
        composable(PhoneNavRoutes.ZAP) {
            ZapDestination(hostFragment = hostFragment)
        }
        composable(PhoneNavRoutes.BACKUP) {
            BackupDestination()
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
            VirtualRemoteDestination(hostFragment = hostFragment)
        }
        composable(PhoneNavRoutes.SETTINGS) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_settings_slot,
                routeTag = PhoneNavRoutes.SETTINGS,
                createFragment = { MyPreferenceFragment() },
            )
        }
        composable(PhoneNavRoutes.HUB) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_hub_slot,
                routeTag = PhoneNavRoutes.HUB,
                createFragment = { ServiceListPager() },
            )
        }
        composable(
            route = PhoneNavRoutes.SERVICE_EPG,
            arguments = listOf(
                navArgument(PhoneNavRoutes.ARG_SERVICE_REF) { type = NavType.StringType },
                navArgument(PhoneNavRoutes.ARG_SERVICE_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val serviceRef = entry.arguments?.getString(PhoneNavRoutes.ARG_SERVICE_REF).orEmpty()
            val serviceName = entry.arguments?.getString(PhoneNavRoutes.ARG_SERVICE_NAME).orEmpty()
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_service_epg_slot,
                routeTag = "service_epg:$serviceRef",
                createFragment = {
                    ServiceEpgListFragment().apply {
                        arguments = Bundle().apply {
                            putString(Event.KEY_SERVICE_REFERENCE, serviceRef)
                            putString(Event.KEY_SERVICE_NAME, serviceName)
                        }
                    }
                },
            )
        }
        composable(
            route = PhoneNavRoutes.EPG_SEARCH,
            arguments = listOf(
                navArgument(PhoneNavRoutes.ARG_QUERY) { type = NavType.StringType },
            ),
        ) { entry ->
            val query = entry.arguments?.getString(PhoneNavRoutes.ARG_QUERY).orEmpty()
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_epg_search_slot,
                routeTag = "epg_search:$query",
                createFragment = {
                    EpgSearchFragment().apply {
                        arguments = Bundle().apply {
                            putString(SearchManager.QUERY, query)
                        }
                    }
                },
            )
        }
        composable(PhoneNavRoutes.PICK_SERVICE) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_pick_service_slot,
                routeTag = PhoneNavRoutes.PICK_SERVICE,
                createFragment = {
                    PickServiceFragment().apply {
                        arguments = Bundle().apply {
                            val data = ExtendedHashMap()
                            data.put(Service.KEY_REFERENCE, "default")
                            putSerializable("data", data)
                            putString("action", Statics.INTENT_ACTION_PICK_BOUQUET)
                        }
                    }
                },
            )
        }
        composable(PhoneNavRoutes.PROFILE_EDIT) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_profile_edit_slot,
                routeTag = hostFragment.profileEditRouteTag(),
                createFragment = {
                    ProfileEditFragment().apply {
                        arguments = hostFragment.profileEditLeafArguments()
                    }
                },
            )
        }
        composable(PhoneNavRoutes.TIMER_EDIT) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_timer_edit_slot,
                routeTag = hostFragment.timerEditRouteTag(),
                createFragment = {
                    TimerEditFragment().apply {
                        arguments = hostFragment.timerEditLeafArguments()
                    }
                },
            )
        }
        composable(PhoneNavRoutes.TIMER_SERVICE_PICK) {
            NestedFragmentDestination(
                hostFragment = hostFragment,
                containerId = R.id.phone_nav_timer_service_pick_slot,
                routeTag = PhoneNavRoutes.TIMER_SERVICE_PICK,
                createFragment = {
                    TimerServicePickFragment().apply {
                        arguments = Bundle().apply {
                            val data = ExtendedHashMap()
                            data.put(Service.KEY_REFERENCE, "default")
                            putSerializable(BaseHttpFragment.sData, data)
                            putString("action", Intent.ACTION_PICK)
                        }
                    }
                },
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
    // Tear down the child Fragment when this route leaves composition. Without
    // this, AndroidView drops the FragmentContainerView but leaves the leaf
    // RESUMED under PhoneNavHostFragment — ServiceListPager's activity-scoped
    // TV/Movies bottom bar then stays visible and blocks drawer Settings.
    DisposableEffect(hostFragment, containerId, routeTag) {
        onDispose {
            removeNestedFragment(hostFragment, containerId, routeTag)
        }
    }
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
 * Mount [createFragment] under the host when missing or when [routeTag] changed
 * (parameterized nested destinations). Never uses `commitNowAllowingStateLoss`;
 * if the child FM has already saved state, defer via [android.view.View.post].
 */
internal fun ensureNestedFragment(
    hostFragment: Fragment,
    containerId: Int,
    routeTag: String,
    createFragment: () -> Fragment,
) {
    if (!hostFragment.isAdded) return
    val fm = hostFragment.childFragmentManager
    val existing = fm.findFragmentById(containerId)
    if (existing != null && existing.tag == routeTag) return
    if (!fm.isStateSaved) {
        commitNestedFragment(fm, containerId, routeTag, createFragment)
        return
    }
    hostFragment.view?.post {
        if (!hostFragment.isAdded) return@post
        val childFm = hostFragment.childFragmentManager
        if (childFm.isStateSaved) return@post
        val still = childFm.findFragmentById(containerId)
        if (still != null && still.tag == routeTag) return@post
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

/**
 * Remove a nested leaf when its Compose route leaves composition so
 * [Fragment.onDestroyView] runs (e.g. hub clears [R.id.tv_movies_nav]).
 */
internal fun removeNestedFragment(
    hostFragment: Fragment,
    containerId: Int,
    routeTag: String,
) {
    if (!hostFragment.isAdded) return
    val fm = hostFragment.childFragmentManager
    val existing = fm.findFragmentById(containerId) ?: return
    if (existing.tag != routeTag) return
    if (fm.isStateSaved) {
        fm.beginTransaction().remove(existing).commitAllowingStateLoss()
        return
    }
    fm.beginTransaction().remove(existing).commitNow()
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

/** Nested EPG search: push onto the NavHost back stack (singleTop avoids duplicate same query). */
fun NavHostController.navigateToEpgSearch(query: String) {
    navigate("epg_search/${Uri.encode(query)}") {
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

fun ComposeView.bindPhoneNavHost(hostFragment: PhoneNavHostFragment) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(hostFragment = hostFragment)
        }
    }
}
