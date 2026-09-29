package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import net.reichholf.dreamdroid.ui.nav.LocalShellSnackbarHostState
import net.reichholf.dreamdroid.ui.settings.SettingsViewModel
import net.reichholf.dreamdroid.ui.settings.TvSettingsScreen
import net.reichholf.dreamdroid.ui.settings.isDebuggable
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
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
    navController: NavHostController = rememberNavController(),
    startDestination: Any = TvHub
) {
    // Activity-scoped: the hub state outlives the hub route and configuration changes.
    val hubViewModel: TvHubViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val shellViewModel: TvShellViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val snackbarHostState = remember { SnackbarHostState() }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalShellSnackbarHostState provides snackbarHostState) {
            TvHubNavGraph(
                activity = activity,
                shellViewModel = shellViewModel,
                hubViewModel = hubViewModel,
                navController = navController,
                startDestination = startDestination
            )
        }
        // TV Material is not Material 3. The snackbar uses the phone theme.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            DreamDroidTheme {
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}

@Composable
private fun TvHubNavGraph(
    activity: ComponentActivity,
    shellViewModel: TvShellViewModel,
    hubViewModel: TvHubViewModel,
    navController: NavHostController,
    startDestination: Any
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
            val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
            ComposeTvHubApp(
                activity = activity,
                gate = shellState.gate,
                onRecheckProfile = shellViewModel::recheck,
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
            TvSettingsDestination(navController, hiltViewModel())
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

/** Any settings change marks the hub for a reload. */
@Composable
private fun TvSettingsDestination(navController: NavHostController, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.uiState.map { it.settings }.distinctUntilChanged().drop(1).collect {
            markTvHubReload(navController.previousBackStackEntry?.savedStateHandle)
        }
    }
    DreamDroidTvTheme {
        TvSettingsScreen(
            settings = uiState.settings,
            onChange = viewModel::update,
            showDeveloperCategory = context.isDebuggable(),
            syncPiconsPathDraft = viewModel.syncPiconsPath.state.takeIf {
                uiState.editingSyncPiconsPath
            },
            onEditSyncPiconsPath = viewModel::editSyncPiconsPath,
            onConfirmSyncPiconsPath = viewModel::confirmSyncPiconsPath,
            onDismissSyncPiconsPath = viewModel::dismissSyncPiconsPath
        )
    }
}
