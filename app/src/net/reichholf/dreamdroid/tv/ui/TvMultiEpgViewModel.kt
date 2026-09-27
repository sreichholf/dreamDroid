package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgSession
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.multiepg.HandleMultiEpgVisibleMinutesAccess
import net.reichholf.dreamdroid.ui.multiepg.newMultiEpgSession
import net.reichholf.dreamdroid.ui.multiepg.readMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.multiepg.writeMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.nav.ShellMessages
import net.reichholf.dreamdroid.ui.nav.mutationResultText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Activity-scoped state for [TvMultiEpgHost]: the loaded grid, the bouquet list, the
 * focused cell, the saved visible-minute span, the open detail, timer editor, and
 * bouquet picker, and the add-timer request.
 */
class TvMultiEpgViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val savedAccess = HandleMultiEpgVisibleMinutesAccess(savedStateHandle)

    var visibleMinutes by mutableIntStateOf(readMultiEpgVisibleMinutes(savedAccess))
        private set

    private val persistGate = MultiEpgPersistGate(
        UserBouquetCache.excludedHubTabRefs(application)
    )

    val session: MultiEpgSession = newMultiEpgSession(application, viewModelScope, persistGate)

    var bouquetRef by mutableStateOf("")
        private set
    var bouquetName by mutableStateOf("")
        private set
    var bouquets by mutableStateOf<List<Service>>(emptyList())
        private set
    var selectedServiceRef by mutableStateOf("")
        private set
    var selectedStartSec by mutableLongStateOf(0L)
        private set
    var setTimerProgress by mutableStateOf<IndeterminateProgressState?>(null)
        private set
    var detailEvent by mutableStateOf<Event?>(null)
        private set
    var editTimerEvent by mutableStateOf<Event?>(null)
        private set
    var pickingBouquet by mutableStateOf(false)
        private set

    private var startJob: Job? = null
    private var setTimerJob: Job? = null

    /** Loads the bouquet list and the launch bouquet once per ViewModel. */
    fun start(extraRef: String?, extraName: String?) {
        if (startJob != null) {
            return
        }
        startJob = viewModelScope.launch {
            val services = loadBouquets()
            val excluded = UserBouquetCache.excludedHubTabRefs(getApplication())
            persistGate.knownTabRefs = UserBouquetCache.userBouquetTabs(services, excluded)
                .map { it.reference }
            bouquets = services.filter { it.reference.isNotBlank() }
            val profile = ProfileRepository.get().requireCurrent()
            val launch = resolveTvMultiEpgLaunchBouquet(
                extraRef = extraRef,
                extraName = extraName,
                defaultRef = profile.defaultBouquetTv.orEmpty(),
                defaultName = profile.defaultBouquetTvName.orEmpty(),
                firstBouquet = bouquets.firstOrNull()
            )
            bouquetRef = launch.first
            bouquetName = launch.second
            session.replaceAndLoad(bouquetRef, MultiEpgNowClock.sec())
        }
    }

    fun select(serviceRef: String, startSec: Long) {
        selectedServiceRef = serviceRef
        selectedStartSec = startSec
    }

    fun reconcileSelection() {
        val next = reconcileTvMultiEpgSelection(
            session.channels,
            selectedServiceRef,
            selectedStartSec
        ) ?: return
        select(next.first, next.second)
    }

    fun jumpToNow(nowSec: Long) {
        select("", 0L)
        session.replaceAndLoad(bouquetRef, nowSec)
    }

    fun pickBouquet(service: Service) {
        pickingBouquet = false
        if (service.reference == bouquetRef) {
            return
        }
        bouquetRef = service.reference
        bouquetName = service.name.ifBlank { service.reference }
        jumpToNow(MultiEpgNowClock.sec())
    }

    fun showDetail(event: Event) {
        detailEvent = event
    }

    fun showTimerEditor(event: Event) {
        editTimerEvent = event
    }

    fun showBouquetPicker() {
        pickingBouquet = true
    }

    fun dismissDetail() {
        detailEvent = null
    }

    fun dismissTimerEditor() {
        editTimerEvent = null
    }

    fun dismissBouquetPicker() {
        pickingBouquet = false
    }

    fun onVisibleMinutesChange(minutes: Int) {
        visibleMinutes = minutes
        writeMultiEpgVisibleMinutes(savedAccess, minutes)
    }

    fun setTimer(event: Event) {
        if (setTimerProgress != null) {
            return
        }
        val app = getApplication<Application>()
        setTimerProgress = IndeterminateProgressState(message = app.getString(R.string.saving))
        setTimerJob = viewModelScope.launchSimpleResultLoad(
            TimerAddByEventIdRequestHandler(),
            Timer.getEventIdParams(event)
        ) { _, result, error ->
            setTimerProgress = null
            setTimerJob = null
            ShellMessages.post(
                mutationResultText(
                    stateText = result.stateText,
                    errorText = error?.resolve(app),
                    fallback = app.getString(R.string.get_content_error)
                )
            )
        }
    }

    override fun onCleared() {
        startJob?.cancel()
        setTimerJob?.cancel()
        session.cancel()
        super.onCleared()
    }

    private suspend fun loadBouquets(): List<Service> {
        val app = getApplication<Application>()
        val profileId = ProfileRepository.get().requireCurrent().id
        val cachedTabs = if (profileId != null) {
            UserBouquetCache.loadTabStripServices(
                AppDatabase.roster(app),
                profileId,
                UserBouquetCache.KIND_TV
            )
        } else {
            emptyList()
        }
        val skipHttp = SessionConnectionHolder.shared.status.value
            .shouldSkipReceiverHttp(cachedTabs.isNotEmpty())
        val live = if (skipHttp) {
            null
        } else {
            loadServiceList(app, listOf(NameValuePair("bRef", TvComposeHubHost.BOUQUETS_TV)))
        }
        return when {
            live != null && live.success -> live.services
            cachedTabs.isNotEmpty() -> cachedTabs
            live != null -> live.services
            else -> emptyList()
        }
    }
}

/**
 * The focused cell after [channels] changed, or null when it still points at a bar.
 * A missing channel falls back to the first one; a missing bar to the bar covering
 * [startSec], then to the channel's first bar.
 */
fun reconcileTvMultiEpgSelection(
    channels: List<MultiEpgChannel>,
    serviceRef: String,
    startSec: Long
): Pair<String, Long>? {
    if (channels.isEmpty()) {
        return null
    }
    val current = channels.find { it.serviceRef == serviceRef }
    if (current == null) {
        val first = channels.first()
        return first.serviceRef to (first.bars.firstOrNull()?.startSec ?: 0L)
    }
    if (current.bars.isEmpty() || current.bars.any { it.startSec == startSec }) {
        return null
    }
    val overlap = current.bars.firstOrNull { bar ->
        bar.startSec <= startSec && bar.endSec > startSec
    }
    return serviceRef to (overlap?.startSec ?: current.bars.first().startSec)
}
