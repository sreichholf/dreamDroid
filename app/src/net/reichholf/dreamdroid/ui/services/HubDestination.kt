package net.reichholf.dreamdroid.ui.services

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.dimensionResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Bouquets
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.launchLocationsAndTagsLoad
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.nav.InstallShellDestinationBar

private const val MODE_TV = "TV"
private const val MODE_RADIO = "Radio"
private const val MODE_MOVIES = "Movies"
private const val MODE_TIMER = "Timer"

/**
 * Phase 2.7h: TV & Movies hub as a direct Compose NavHost destination.
 * Owns mode + bouquet/location tabs (parity with former ServiceListPager),
 * hosts [TvMoviesDestinationBar] via [InstallShellDestinationBar] on [R.id.shell_destination_nav]
 * (Scaffold bottomBar inside detail_view sits under the system nav — dualpane ScrollingViewBehavior),
 * and routes MultiChoice / timer-edit results for the active child page.
 */
@Composable
fun HubDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(MODE_TV) }
    var currentTv by rememberSaveable { mutableStateOf<String?>(null) }
    var currentRadio by rememberSaveable { mutableStateOf<String?>(null) }
    var currentMovie by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRow by rememberSaveable { mutableIntStateOf(0) }
    var timerRemountEpoch by rememberSaveable { mutableIntStateOf(0) }

    var bouquets by remember { mutableStateOf<Bouquets?>(null) }
    var bouquetError by remember { mutableStateOf<String?>(null) }
    var locationsReady by remember {
        mutableStateOf(DreamDroid.getLocations().isNotEmpty())
    }

    val movieSession = remember { HubMovieListSession() }

    val tvBouquets = remember(bouquets) {
        buildDedicatedBouquets(
            bouquets?.tv.orEmpty(),
            context.resources.getStringArray(R.array.servicelist_dedicated),
            context.resources.getStringArray(R.array.servicerefstv),
        )
    }
    val radioBouquets = remember(bouquets) {
        buildDedicatedBouquets(
            bouquets?.radio.orEmpty(),
            context.resources.getStringArray(R.array.servicelist_dedicated),
            context.resources.getStringArray(R.array.servicerefsradio),
        )
    }
    val movieLocations = remember(locationsReady) {
        if (locationsReady) DreamDroid.getLocations().toList() else emptyList()
    }

    val rows = when (mode) {
        MODE_TV -> tvBouquets.map { it.name }
        MODE_RADIO -> radioBouquets.map { it.name }
        MODE_MOVIES -> movieLocations
        else -> emptyList()
    }

    val hubSelected = when (mode) {
        MODE_RADIO -> TvMoviesDestination.RADIO
        MODE_MOVIES -> TvMoviesDestination.MOVIES
        MODE_TIMER -> TvMoviesDestination.TIMER
        else -> TvMoviesDestination.TV
    }

    fun selectDestination(dest: TvMoviesDestination) {
        when (dest) {
            TvMoviesDestination.TV -> {
                mode = MODE_TV
                selectedRow = indexOfRef(tvBouquets, currentTv ?: DreamDroid.getCurrentProfile().defaultBouquetTv)
            }
            TvMoviesDestination.RADIO -> {
                mode = MODE_RADIO
                selectedRow = indexOfRef(radioBouquets, currentRadio)
            }
            TvMoviesDestination.MOVIES -> {
                mode = MODE_MOVIES
                if (!locationsReady || movieLocations.isEmpty()) {
                    Toast.makeText(context, R.string.loading, Toast.LENGTH_SHORT).show()
                    selectedRow = 0
                } else {
                    selectedRow = indexOfLocation(movieLocations, currentMovie)
                }
            }
            TvMoviesDestination.TIMER -> {
                mode = MODE_TIMER
                selectedRow = 0
            }
        }
    }

    /** Active TV/Radio service list's go-up (clear drill-down / reload root). */
    var serviceListGoUp by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Shell destination bar state (Coordinator slot). Keep handler/selection fresh each frame.
    val destinationBarState = remember { TvMoviesHubState() }
    destinationBarState.selected = hubSelected
    destinationBarState.onDestinationSelected = { selectDestination(it) }

    fun onRowSelected(index: Int) {
        // Reselect active bouquet tab → go up one provider/directory level (or reload root).
        if (index == selectedRow && (mode == MODE_TV || mode == MODE_RADIO)) {
            serviceListGoUp?.invoke()
            return
        }
        selectedRow = index
        when (mode) {
            MODE_TV -> currentTv = tvBouquets.getOrNull(index)?.reference
            MODE_RADIO -> currentRadio = radioBouquets.getOrNull(index)?.reference
            MODE_MOVIES -> currentMovie = movieLocations.getOrNull(index)
        }
    }

    // Destination bar on activity Coordinator (shell_destination_nav). Putting it in Scaffold
    // bottomBar inside detail_view pushes it under the system gesture nav.
    InstallShellDestinationBar {
        TvMoviesDestinationBar(
            selected = destinationBarState.selected,
            onDestinationSelected = { destinationBarState.onDestinationSelected(it) },
        )
    }

    DisposableEffect(hostFragment) {
        val listener = PhoneNavHostFragment.ActivityResultListener { requestCode, resultCode, _ ->
            if (requestCode == Statics.REQUEST_EDIT_TIMER && resultCode == Activity.RESULT_OK) {
                timerRemountEpoch += 1
            }
        }
        hostFragment.composeActivityResultListener = listener
        onDispose {
            if (hostFragment.composeActivityResultListener === listener) {
                hostFragment.composeActivityResultListener = null
            }
        }
    }

    LaunchedEffect(Unit) {
        val result = loadBouquetList(context.applicationContext)
        bouquets = result.bouquets
        bouquetError = result.errorText
        when (mode) {
            MODE_TV -> {
                val list = buildDedicatedBouquets(
                    result.bouquets.tv,
                    context.resources.getStringArray(R.array.servicelist_dedicated),
                    context.resources.getStringArray(R.array.servicerefstv),
                )
                selectedRow = indexOfRef(list, currentTv ?: DreamDroid.getCurrentProfile().defaultBouquetTv)
            }
            MODE_RADIO -> {
                val list = buildDedicatedBouquets(
                    result.bouquets.radio,
                    context.resources.getStringArray(R.array.servicelist_dedicated),
                    context.resources.getStringArray(R.array.servicerefsradio),
                )
                selectedRow = indexOfRef(list, currentRadio)
            }
            MODE_MOVIES -> {
                if (locationsReady) {
                    selectedRow = indexOfLocation(DreamDroid.getLocations().toList(), currentMovie)
                }
            }
            else -> selectedRow = 0
        }
    }

    DisposableEffect(hostFragment) {
        val job = hostFragment.launchLocationsAndTagsLoad(
            onProgress = { _, _ -> },
            onReady = {
                locationsReady = true
                if (mode == MODE_MOVIES) {
                    selectedRow = indexOfLocation(DreamDroid.getLocations().toList(), currentMovie)
                }
            },
        )
        onDispose { job.cancel() }
    }

    // Clamp selectedRow when the active row list shrinks (e.g. rotation before load).
    LaunchedEffect(mode, rows.size) {
        if (rows.isNotEmpty() && selectedRow > rows.lastIndex) {
            selectedRow = rows.lastIndex
        } else if (rows.isEmpty()) {
            selectedRow = 0
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TvMoviesHeader(
                rows = rows,
                selectedRow = if (rows.isEmpty()) 0 else selectedRow.coerceIn(0, rows.lastIndex),
                error = bouquetError,
                onRowSelected = { onRowSelected(it) },
            )
            // Clip list pages so pull-to-refresh glyphs cannot paint over bouquet tabs above.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                when (mode) {
                    MODE_TV -> {
                        val bouquet = tvBouquets.getOrNull(
                            if (tvBouquets.isEmpty()) 0 else selectedRow.coerceIn(0, tvBouquets.lastIndex),
                        )
                        if (bouquet != null) {
                            key(bouquet.reference) {
                                HubServiceListPage(
                                    hostFragment = hostFragment,
                                    bouquetRef = bouquet.reference,
                                    bouquetName = bouquet.name,
                                    onProvideGoUp = { serviceListGoUp = it },
                                )
                            }
                        }
                    }
                    MODE_RADIO -> {
                        val bouquet = radioBouquets.getOrNull(
                            if (radioBouquets.isEmpty()) 0 else selectedRow.coerceIn(0, radioBouquets.lastIndex),
                        )
                        if (bouquet != null) {
                            key(bouquet.reference) {
                                HubServiceListPage(
                                    hostFragment = hostFragment,
                                    bouquetRef = bouquet.reference,
                                    bouquetName = bouquet.name,
                                    onProvideGoUp = { serviceListGoUp = it },
                                )
                            }
                        }
                    }
                    MODE_MOVIES -> {
                        val locationIndex = if (movieLocations.isEmpty()) {
                            0
                        } else {
                            selectedRow.coerceIn(0, movieLocations.lastIndex)
                        }
                        val location = movieLocations.getOrNull(locationIndex)
                        if (location != null) {
                            key(location) {
                                HubMovieListPage(
                                    hostFragment = hostFragment,
                                    location = location,
                                    locationIndex = locationIndex,
                                    session = movieSession,
                                )
                            }
                        }
                    }
                    MODE_TIMER -> {
                        HubTimerListPage(
                            hostFragment = hostFragment,
                            remountEpoch = timerRemountEpoch,
                        )
                    }
                }
            }
            // Reserve space for the Coordinator-hosted destination bar (dualpane shell_destination_nav).
            Spacer(
                Modifier.height(dimensionResource(R.dimen.shell_destination_bar_height)),
            )
        }
    }
}

private fun buildDedicatedBouquets(
    loaded: List<Service>,
    labels: Array<String>,
    refs: Array<String>,
): List<Service> {
    val items = ArrayList<Service>()
    var start = 0
    if (loaded.isNotEmpty()) {
        start = 1
        items.addAll(loaded)
    }
    for (i in start until labels.size) {
        items.add(Service(refs[i], labels[i]))
    }
    return items
}

private fun indexOfRef(items: List<Service>, ref: String?): Int {
    if (ref.isNullOrEmpty() || items.isEmpty()) return 0
    val idx = items.indexOfFirst { it.reference == ref }
    return if (idx >= 0) idx else 0
}

private fun indexOfLocation(items: List<String>, location: String?): Int {
    if (location.isNullOrEmpty() || items.isEmpty()) return 0
    val idx = items.indexOf(location)
    return if (idx >= 0) idx else 0
}
