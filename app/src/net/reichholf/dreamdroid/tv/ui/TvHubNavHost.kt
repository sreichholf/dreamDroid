package net.reichholf.dreamdroid.tv.ui

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.ui.settings.SettingsState
import net.reichholf.dreamdroid.ui.settings.TvSettingsScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

/** Hub entry flag. A settings write or a profile save sets it; the hub consumes it. */
internal const val TV_HUB_RELOAD_KEY: String = "tv_hub_reload"

internal fun markTvHubReload(handle: SavedStateHandle?) {
    handle?.set(TV_HUB_RELOAD_KEY, true)
}

/**
 * True once. Clearing the flag keeps a later visit from reloading when the user
 * backed out without a new save.
 */
internal fun consumeTvHubReload(handle: SavedStateHandle): Boolean {
    if (handle.get<Boolean>(TV_HUB_RELOAD_KEY) != true) {
        return false
    }
    handle[TV_HUB_RELOAD_KEY] = false
    return true
}

@Composable
fun TvHubNavHost(
    activity: ComponentActivity,
    onRecheckProfile: () -> Unit,
    hubViewModel: TvHubViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = TvHubViewModel.Factory
    ),
    navController: NavHostController = rememberNavController(),
    startDestination: Any = TvHub
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<TvHub> { entry ->
            val reload by entry.savedStateHandle
                .getStateFlow(TV_HUB_RELOAD_KEY, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(reload) {
                if (consumeTvHubReload(entry.savedStateHandle)) {
                    hubViewModel.reload()
                }
            }
            ComposeTvHubApp(
                activity = activity,
                onRecheckProfile = onRecheckProfile,
                viewModel = hubViewModel,
                onOpenSettings = { navController.navigate(TvSettings) },
                onOpenProfiles = { navController.navigate(TvProfiles) },
                onOpenMultiEpg = { reference, name ->
                    navController.navigate(
                        TvComposeHubHost.tvMultiEpgRoute(reference, name)
                    )
                }
            )
        }
        composable<TvMultiEpg> { entry ->
            val route = entry.toRoute<TvMultiEpg>()
            TvMultiEpgHost(
                activity = activity,
                bouquetRef = route.bouquetRef,
                bouquetName = route.bouquetName
            )
        }
        composable<TvSettings> {
            TvSettingsDestination(navController)
        }
        composable<TvProfiles> {
            DreamDroidTvTheme {
                TvProfilesHost(
                    onMarkReload = {
                        markTvHubReload(
                            navController.previousBackStackEntry?.savedStateHandle
                        )
                    },
                    onLeave = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun TvSettingsDestination(navController: NavHostController) {
    val context = LocalContext.current
    val state = remember { SettingsState.create(context) }
    DisposableEffect(navController) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            markTvHubReload(navController.previousBackStackEntry?.savedStateHandle)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    DreamDroidTvTheme {
        TvSettingsScreen(state = state)
    }
}
