package net.reichholf.dreamdroid.ui.nav

import android.app.SearchManager
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
import net.reichholf.dreamdroid.fragment.BackupFragment
import net.reichholf.dreamdroid.fragment.CurrentServiceFragment
import net.reichholf.dreamdroid.fragment.DeviceInfoFragment
import net.reichholf.dreamdroid.fragment.EpgBouquetFragment
import net.reichholf.dreamdroid.fragment.EpgSearchFragment
import net.reichholf.dreamdroid.fragment.MyPreferenceFragment
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.PickServiceFragment
import net.reichholf.dreamdroid.fragment.ProfileEditFragment
import net.reichholf.dreamdroid.fragment.ProfileListFragment
import net.reichholf.dreamdroid.fragment.ScreenShotFragment
import net.reichholf.dreamdroid.fragment.ServiceEpgListFragment
import net.reichholf.dreamdroid.fragment.ServiceListPager
import net.reichholf.dreamdroid.fragment.SignalFragment
import net.reichholf.dreamdroid.fragment.VirtualRemotePagerFragment
import net.reichholf.dreamdroid.fragment.ZapFragment
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Phone shell [NavHost]. Drawer leaves through hub + settings; nested service EPG, EPG search,
 * and bouquet pick are the 2.1f beachheads.
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

fun ComposeView.bindPhoneNavHost(hostFragment: PhoneNavHostFragment) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            PhoneNavHost(hostFragment = hostFragment)
        }
    }
}
