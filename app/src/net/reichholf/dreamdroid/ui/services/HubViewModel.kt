package net.reichholf.dreamdroid.ui.services

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Bouquets
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.shouldWaitForDeviceInfo

/**
 * Hub mode, selected row, and bouquet or location refs for [HubDestination].
 * Bouquet and movie-location loads stay on [viewModelScope], so leaving the
 * destination and popping back keeps the strip. Child lists use their own
 * ViewModels on this same back-stack entry.
 */
class HubViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val savedAccess = HandleHubShellSavedAccess(savedStateHandle)

    var mode by mutableStateOf(HubModes.TV)
        private set

    var currentTv by mutableStateOf<String?>(null)
        private set

    var currentRadio by mutableStateOf<String?>(null)
        private set

    var currentMovie by mutableStateOf<String?>(null)
        private set

    var selectedRow by mutableIntStateOf(0)
        private set

    var timerRemountEpoch by mutableIntStateOf(0)
        private set

    var nowPlayingReloadEpoch by mutableIntStateOf(0)
        private set

    var bouquets by mutableStateOf<Bouquets?>(null)
        private set

    var bouquetError by mutableStateOf<String?>(null)
        private set

    var locationsReady by mutableStateOf(false)
        private set

    var movieLocations by mutableStateOf<List<String>>(emptyList())
        private set

    private var bouquetConnection: String? = null
    private var bouquetJob: Job? = null
    private var locationsStarted = false
    private var locationsJob: Job? = null

    init {
        val saved = readHubShellSaved(savedAccess)
        mode = saved.mode
        currentTv = saved.currentTv
        currentRadio = saved.currentRadio
        currentMovie = saved.currentMovie
        selectedRow = saved.selectedRow
        timerRemountEpoch = saved.timerRemountEpoch
        nowPlayingReloadEpoch = saved.nowPlayingReloadEpoch
        val knownLocations = DreamDroid.getLocations()
        locationsReady = knownLocations.isNotEmpty()
        if (knownLocations.isNotEmpty()) {
            movieLocations = knownLocations.toList()
        }
    }

    fun selectTv(tvBouquets: List<Service>) {
        mode = HubModes.TV
        val (idx, ref) = resolveBouquetSelection(
            tvBouquets,
            currentTv,
            DreamDroid.getCurrentProfile().defaultBouquetTv
        )
        selectedRow = idx
        currentTv = ref
        persist()
    }

    fun selectRadio(radioBouquets: List<Service>) {
        mode = HubModes.RADIO
        val (idx, ref) = resolveBouquetSelection(radioBouquets, currentRadio, null)
        selectedRow = idx
        currentRadio = ref
        persist()
    }

    /** Returns false when movie locations are not ready yet. */
    fun selectMovies(): Boolean {
        mode = HubModes.MOVIES
        if (!locationsReady || movieLocations.isEmpty()) {
            selectedRow = 0
            persist()
            return false
        }
        selectedRow = indexOfLocation(movieLocations, currentMovie)
        persist()
        return true
    }

    fun selectTimer() {
        mode = HubModes.TIMER
        selectedRow = 0
        persist()
    }

    fun onRowSelected(index: Int, refForMode: String?) {
        selectedRow = index
        when (mode) {
            HubModes.TV -> currentTv = refForMode
            HubModes.RADIO -> currentRadio = refForMode
            HubModes.MOVIES -> currentMovie = refForMode
        }
        persist()
    }

    fun bumpTimerRemount() {
        timerRemountEpoch += 1
        persist()
    }

    fun bumpNowPlayingReload() {
        nowPlayingReloadEpoch += 1
        persist()
    }

    fun clampSelectedRow(rowCount: Int) {
        val next = when {
            rowCount > 0 && selectedRow > rowCount - 1 -> rowCount - 1
            rowCount == 0 -> 0
            else -> return
        }
        if (next == selectedRow) {
            return
        }
        selectedRow = next
        persist()
    }

    fun onBouquetConnection(connection: ConnectionStatus.Session?) {
        val key = connection?.name ?: "none"
        val inFlight = bouquets != null || bouquetJob?.isActive == true
        if (!shouldLoadHubPage(bouquetConnection, key) && inFlight) {
            return
        }
        bouquetConnection = key
        bouquetJob?.cancel()
        bouquetJob = viewModelScope.launch { loadBouquets() }
    }

    fun ensureLocations(handle: PhoneNavHandle) {
        if (locationsStarted) {
            return
        }
        locationsStarted = true
        val app = getApplication<Application>()
        locationsJob = handle.launchLocationsAndTagsLoad(
            onProgress = { _, _ -> },
            onReady = { },
            onLocationsResult = { success ->
                viewModelScope.launch {
                    val painted = movieLocationsAfterHttpOrCache(
                        AppDatabase.movie(app),
                        DreamDroid.getCurrentProfile().id,
                        success,
                        DreamDroid.getLocations().toList()
                    )
                    movieLocations = painted
                    locationsReady = true
                    if (mode == HubModes.MOVIES) {
                        selectedRow = indexOfLocation(painted, currentMovie)
                        persist()
                    }
                }
            }
        )
    }

    override fun onCleared() {
        bouquetJob?.cancel()
        locationsJob?.cancel()
        super.onCleared()
    }

    private suspend fun loadBouquets() {
        val app = getApplication<Application>()
        val profileId = DreamDroid.getCurrentProfile().id
        val excluded = UserBouquetCache.excludedHubTabRefs(app)
        val dao = if (profileId != null) AppDatabase.roster(app) else null
        val cachedTv = if (dao != null && profileId != null) {
            UserBouquetCache.loadTabStripServices(dao, profileId, UserBouquetCache.KIND_TV)
        } else {
            emptyList()
        }
        val cachedRadio = if (dao != null && profileId != null) {
            UserBouquetCache.loadTabStripServices(dao, profileId, UserBouquetCache.KIND_RADIO)
        } else {
            emptyList()
        }
        val hasStrip = cachedTv.isNotEmpty() || cachedRadio.isNotEmpty()
        if (hasStrip) {
            val cached = Bouquets()
            cached.tv.addAll(cachedTv)
            cached.radio.addAll(cachedRadio)
            applyPaintedBouquets(app, cached, error = null)
        }
        val status = SessionConnectionHolder.shared.status.value
        if (status.shouldSkipReceiverHttp(hasStrip)) {
            return
        }
        if (shouldWaitForDeviceInfo(hasStrip)) {
            withTimeoutOrNull(20_000) {
                while (DreamDroid.getCurrentProfile().cachedDeviceInfo == null) {
                    delay(100)
                }
            }
        }
        val result = loadBouquetList(app)
        var painted = result.bouquets
        var usedCache = false
        if (dao != null && profileId != null) {
            if (result.tvLoaded) {
                UserBouquetCache.replaceTabStrip(
                    dao,
                    profileId,
                    UserBouquetCache.KIND_TV,
                    result.bouquets.tv,
                    excluded
                )
            }
            if (result.radioLoaded) {
                UserBouquetCache.replaceTabStrip(
                    dao,
                    profileId,
                    UserBouquetCache.KIND_RADIO,
                    result.bouquets.radio,
                    excluded
                )
            }
            val resolved = bouquetsAfterHttpOrCache(
                result.success,
                result.bouquets,
                UserBouquetCache.loadTabStripServices(
                    dao,
                    profileId,
                    UserBouquetCache.KIND_TV
                ),
                UserBouquetCache.loadTabStripServices(
                    dao,
                    profileId,
                    UserBouquetCache.KIND_RADIO
                )
            )
            painted = resolved.first
            usedCache = resolved.second
        }
        applyPaintedBouquets(app, painted, if (usedCache) null else result.errorText)
    }

    private fun applyPaintedBouquets(app: Application, painted: Bouquets, error: String?) {
        bouquets = painted
        bouquetError = error
        when (mode) {
            HubModes.TV -> {
                val list = buildDedicatedBouquets(
                    painted.tv,
                    app.resources.getStringArray(R.array.servicelist_dedicated),
                    app.resources.getStringArray(R.array.servicerefstv)
                )
                val (idx, ref) = resolveBouquetSelection(
                    list,
                    currentTv,
                    DreamDroid.getCurrentProfile().defaultBouquetTv
                )
                selectedRow = idx
                currentTv = ref
            }

            HubModes.RADIO -> {
                val list = buildDedicatedBouquets(
                    painted.radio,
                    app.resources.getStringArray(R.array.servicelist_dedicated),
                    app.resources.getStringArray(R.array.servicerefsradio)
                )
                val (idx, ref) = resolveBouquetSelection(list, currentRadio, null)
                selectedRow = idx
                currentRadio = ref
            }

            HubModes.MOVIES -> {
                if (locationsReady) {
                    selectedRow = indexOfLocation(movieLocations, currentMovie)
                }
            }

            else -> selectedRow = 0
        }
        persist()
    }

    private fun persist() {
        HubShellSaved(
            mode = mode,
            currentTv = currentTv,
            currentRadio = currentRadio,
            currentMovie = currentMovie,
            selectedRow = selectedRow,
            timerRemountEpoch = timerRemountEpoch,
            nowPlayingReloadEpoch = nowPlayingReloadEpoch
        ).writeTo(savedAccess)
    }
}

private class HandleHubShellSavedAccess(private val handle: SavedStateHandle) :
    HubShellSavedAccess {
    override fun getMode(): String? = handle.get<String>(HubShellSavedKeys.MODE)

    override fun setMode(mode: String) {
        handle[HubShellSavedKeys.MODE] = mode
    }

    override fun getCurrentTv(): String? = handle.get<String>(HubShellSavedKeys.CURRENT_TV)

    override fun setCurrentTv(currentTv: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_TV, currentTv)
    }

    override fun getCurrentRadio(): String? = handle.get<String>(HubShellSavedKeys.CURRENT_RADIO)

    override fun setCurrentRadio(currentRadio: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_RADIO, currentRadio)
    }

    override fun getCurrentMovie(): String? = handle.get<String>(HubShellSavedKeys.CURRENT_MOVIE)

    override fun setCurrentMovie(currentMovie: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_MOVIE, currentMovie)
    }

    override fun getSelectedRow(): Int? = handle.get<Int>(HubShellSavedKeys.SELECTED_ROW)

    override fun setSelectedRow(selectedRow: Int) {
        handle[HubShellSavedKeys.SELECTED_ROW] = selectedRow
    }

    override fun getTimerRemountEpoch(): Int? =
        handle.get<Int>(HubShellSavedKeys.TIMER_REMOUNT_EPOCH)

    override fun setTimerRemountEpoch(epoch: Int) {
        handle[HubShellSavedKeys.TIMER_REMOUNT_EPOCH] = epoch
    }

    override fun getNowPlayingReloadEpoch(): Int? =
        handle.get<Int>(HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH)

    override fun setNowPlayingReloadEpoch(epoch: Int) {
        handle[HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH] = epoch
    }

    private fun putOrRemove(key: String, value: String?) {
        if (value == null) {
            handle.remove<String>(key)
        } else {
            handle[key] = value
        }
    }
}

private fun indexOfLocation(items: List<String>, location: String?): Int {
    if (location.isNullOrEmpty() || items.isEmpty()) return 0
    val idx = items.indexOf(location)
    return if (idx >= 0) idx else 0
}
