package net.reichholf.dreamdroid.ui.services

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.room.MovieDao
import net.reichholf.dreamdroid.room.MovieSnapshotStore
import net.reichholf.dreamdroid.ui.current.HubNowPlaying
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.RegisterShellDestinationBar
import net.reichholf.dreamdroid.ui.nav.ShellDestinationBarContent
import net.reichholf.dreamdroid.ui.nav.ShellHubBottomChromeSpacer
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7h: TV & Movies hub as a direct Compose NavHost destination.
 * [HubViewModel] owns mode, the selected row, and bouquet or location tabs
 * (parity with former ServiceListPager). Publishes [TvMoviesHubState] through
 * [RegisterShellDestinationBar] (phone [R.id.shell_destination_nav] bar or tablet
 * [R.id.shell_destination_rail]), and routes MultiChoice / timer-edit results
 * for the active child page.
 */
@Composable
fun HubDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: HubViewModel = viewModel()
) {
    val context = LocalContext.current
    val mode = viewModel.mode
    val selectedRow = viewModel.selectedRow
    val bouquets = viewModel.bouquets
    val bouquetError = viewModel.bouquetError
    val movieLocations = viewModel.movieLocations
    val nowPlayingReloadEpoch = viewModel.nowPlayingReloadEpoch

    val tvBouquets = remember(bouquets) {
        buildDedicatedBouquets(
            bouquets?.tv.orEmpty(),
            context.resources.getStringArray(R.array.servicelist_dedicated),
            context.resources.getStringArray(R.array.servicerefstv)
        )
    }
    val radioBouquets = remember(bouquets) {
        buildDedicatedBouquets(
            bouquets?.radio.orEmpty(),
            context.resources.getStringArray(R.array.servicelist_dedicated),
            context.resources.getStringArray(R.array.servicerefsradio)
        )
    }
    val rows = when (mode) {
        HubModes.TV -> tvBouquets.map { it.name }
        HubModes.RADIO -> radioBouquets.map { it.name }
        HubModes.MOVIES -> movieLocations
        else -> emptyList()
    }

    val hubSelected = when (mode) {
        HubModes.RADIO -> TvMoviesDestination.RADIO
        HubModes.MOVIES -> TvMoviesDestination.MOVIES
        HubModes.TIMER -> TvMoviesDestination.TIMER
        else -> TvMoviesDestination.TV
    }

    fun selectDestination(dest: TvMoviesDestination) {
        when (dest) {
            TvMoviesDestination.TV -> viewModel.selectTv(tvBouquets)

            TvMoviesDestination.RADIO -> viewModel.selectRadio(radioBouquets)

            TvMoviesDestination.MOVIES -> {
                if (!viewModel.selectMovies()) {
                    Toast.makeText(context, R.string.loading, Toast.LENGTH_SHORT).show()
                }
            }

            TvMoviesDestination.TIMER -> viewModel.selectTimer()
        }
    }

    /** Active TV/Radio service list's go-up (clear drill-down / reload root). */
    var serviceListGoUp by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Shell destination bar state (Coordinator slot). Keep handler/selection fresh each frame.
    val destinationBarState = remember { TvMoviesHubState() }
    destinationBarState.selected = hubSelected
    destinationBarState.onDestinationSelected = { selectDestination(it) }

    HubNowPlaying(
        handle = handle,
        reloadEpoch = nowPlayingReloadEpoch,
        hubState = destinationBarState
    )

    fun onRowSelected(index: Int) {
        // Reselect active bouquet tab → go up one provider/directory level (or reload root).
        if (index == selectedRow && (mode == HubModes.TV || mode == HubModes.RADIO)) {
            serviceListGoUp?.invoke()
            return
        }
        val ref = when (mode) {
            HubModes.TV -> tvBouquets.getOrNull(index)?.reference
            HubModes.RADIO -> radioBouquets.getOrNull(index)?.reference
            HubModes.MOVIES -> movieLocations.getOrNull(index)
            else -> null
        }
        viewModel.onRowSelected(index, ref)
    }

    // Publish Snapshot state to the NavHost-owned shell ComposeView. Installing
    // shell_destination_nav from this leaf tied chrome disposal to hub content load
    // (bar vanished after bouquet / list refresh finished).
    RegisterShellDestinationBar(ShellDestinationBarContent.TvMovies(destinationBarState))

    DisposableEffect(handle) {
        val listener = PhoneNavHandle.ActivityResultListener { requestCode, resultCode, _ ->
            if (requestCode == Statics.REQUEST_EDIT_TIMER && resultCode == Activity.RESULT_OK) {
                viewModel.bumpTimerRemount()
            }
        }
        handle.composeActivityResultListener = listener
        onDispose {
            if (handle.composeActivityResultListener === listener) {
                handle.composeActivityResultListener = null
            }
        }
    }

    val connectionSession = SessionConnectionHolder.shared.status.collectAsState().value.session
    LaunchedEffect(connectionSession) { viewModel.onBouquetConnection(connectionSession) }

    LaunchedEffect(handle) { viewModel.ensureLocations(handle) }

    // Clamp selectedRow when the active row list shrinks (e.g. rotation before load).
    LaunchedEffect(mode, rows.size) { viewModel.clampSelectedRow(rows.size) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // zIndex above the list: stock PullToRefreshContainer sits TopCenter in the
            // list slot; Column draws later children on top, so without this the elevated
            // indicator can paint over bouquet tab labels (e.g. Provider).
            TvMoviesHeader(
                rows = rows,
                selectedRow = if (rows.isEmpty()) 0 else selectedRow.coerceIn(0, rows.lastIndex),
                error = bouquetError,
                onRowSelected = { onRowSelected(it) },
                modifier = Modifier.zIndex(1f)
            )
            // Clip list pages so mid-pull glyphs cannot paint outside the list slot.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                when (mode) {
                    HubModes.TV -> {
                        // Wait for bouquet roots (old ServiceListPager stayed empty until then).
                        if (bouquets != null) {
                            val bouquet = tvBouquets.getOrNull(
                                if (tvBouquets.isEmpty()) {
                                    0
                                } else {
                                    selectedRow.coerceIn(
                                        0,
                                        tvBouquets.lastIndex
                                    )
                                }
                            )
                            if (bouquet != null) {
                                key(bouquet.reference) {
                                    HubServiceListPage(
                                        handle = handle,
                                        bouquetRef = bouquet.reference,
                                        bouquetName = bouquet.name,
                                        onProvideGoUp = { serviceListGoUp = it },
                                        onZapped = { viewModel.bumpNowPlayingReload() }
                                    )
                                }
                            }
                        }
                    }

                    HubModes.RADIO -> {
                        if (bouquets != null) {
                            val bouquet = radioBouquets.getOrNull(
                                if (radioBouquets.isEmpty()) {
                                    0
                                } else {
                                    selectedRow.coerceIn(
                                        0,
                                        radioBouquets.lastIndex
                                    )
                                }
                            )
                            if (bouquet != null) {
                                key(bouquet.reference) {
                                    HubServiceListPage(
                                        handle = handle,
                                        bouquetRef = bouquet.reference,
                                        bouquetName = bouquet.name,
                                        onProvideGoUp = { serviceListGoUp = it },
                                        onZapped = { viewModel.bumpNowPlayingReload() }
                                    )
                                }
                            }
                        }
                    }

                    HubModes.MOVIES -> {
                        val locationIndex = if (movieLocations.isEmpty()) {
                            0
                        } else {
                            selectedRow.coerceIn(0, movieLocations.lastIndex)
                        }
                        val location = movieLocations.getOrNull(locationIndex)
                        if (location != null) {
                            key(location) {
                                HubMovieListPage(
                                    handle = handle,
                                    location = location,
                                    locationIndex = locationIndex
                                )
                            }
                        }
                    }

                    HubModes.TIMER -> {
                        HubTimerListPage(
                            handle = handle,
                            remountEpoch = viewModel.timerRemountEpoch
                        )
                    }
                }
            }
            ShellHubBottomChromeSpacer(
                nowPlayingStripEnabled = destinationBarState.nowPlayingStripEnabled
            )
        }
    }
}

/**
 * HTTP movie locations, or the Room strip when the request failed and a strip
 * exists. Dedicated Provider/All tabs are N/A for movies.
 */
internal suspend fun movieLocationsAfterHttpOrCache(
    dao: MovieDao?,
    profileId: Int?,
    httpSuccess: Boolean,
    liveLocations: List<String>
): List<String> {
    if (httpSuccess) {
        if (dao != null && profileId != null) {
            MovieSnapshotStore.replaceLocations(dao, profileId, liveLocations)
        }
        return liveLocations
    }
    val cached = if (dao != null && profileId != null) {
        MovieSnapshotStore.loadLocations(dao, profileId)
    } else {
        null
    }
    return cached ?: liveLocations
}
