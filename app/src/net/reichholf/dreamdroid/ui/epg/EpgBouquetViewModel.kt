package net.reichholf.dreamdroid.ui.epg

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgDao
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Owns bouquet identity, time, [EpgBouquetListState], refresh, and the list load.
 * The load stays on [viewModelScope] so leaving the destination does not cancel it.
 * A missing saved time stays null until the first bind. Date and time picker visibility
 * and navigation stay in [EpgBouquetDestination].
 */
class EpgBouquetViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val listState: EpgBouquetListState = EpgBouquetListState()
    val refresh: ComposeRefreshState = ComposeRefreshState()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var bouquetRef by mutableStateOf("")
        private set

    var bouquetName by mutableStateOf("")
        private set

    var timeSec by mutableStateOf<Int?>(null)
        private set

    var waitingForPicker by mutableStateOf(false)
        private set

    internal var loadHooks: EpgBouquetLoadHooks = EpgBouquetLoadHooks()

    private val pickBouquetChannel = Channel<Int>(Channel.CONFLATED)
    val pickBouquetRequests: Flow<Int> = pickBouquetChannel.receiveAsFlow()

    private val savedAccess = HandleEpgBouquetNavSavedAccess(savedStateHandle)
    private var saved = readEpgBouquetNavSaved(savedAccess)
    private var seenEpoch: Int? = null
    internal var loadJob: Job? = null
        private set

    init {
        bouquetRef = saved.bouquetRef
        bouquetName = saved.bouquetName
        waitingForPicker = saved.waitingForPicker
        timeSec = saved.timeSec?.toInt()
    }

    /**
     * First bind keeps a restored snapshot. A later [epoch] resets bouquet ref, name, and
     * time the way `rememberSaveable(remountEpoch)` did. Waiting for the picker is not reset.
     * A missing time key stays null until this seeds it from [leafTimeSec] or [nowSec].
     */
    fun ensureEpoch(
        epoch: Int,
        leafRef: String,
        leafName: String,
        leafTimeSec: Long?,
        nowSec: Int
    ) {
        if (seenEpoch == null) {
            seedIfAbsent(leafRef, leafName, leafTimeSec, nowSec)
            seenEpoch = epoch
            return
        }
        if (seenEpoch == epoch) {
            return
        }
        loadJob?.cancel()
        loadJob = null
        persist(
            saved.copy(
                bouquetRef = leafRef,
                bouquetName = leafName,
                timeSec = resolvedTimeSec(leafTimeSec, nowSec)
            )
        )
        listState.replaceAll(emptyList())
        listState.scrollToTop()
        emptyMessage = null
        refresh.setRefreshing(false)
        seenEpoch = epoch
    }

    fun applyLeaf(leafRef: String, leafName: String) {
        val resolvedRef = EpgBouquetRestore.resolveRef(leafRef, bouquetRef)
        val resolvedName = EpgBouquetRestore.resolveName(leafName, bouquetName, bouquetRef)
        if (resolvedRef != bouquetRef) {
            persist(saved.copy(bouquetRef = resolvedRef, bouquetName = resolvedName))
            listState.scrollToTop()
        }
    }

    fun reload(forceRefresh: Boolean = false) {
        if (bouquetRef.isEmpty() && !waitingForPicker) {
            pickBouquet()
            return
        }
        if (bouquetRef.isEmpty()) {
            return
        }
        val atSec = timeSec ?: return
        val app = getApplication<Application>()
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        loadJob?.cancel()
        val ref = bouquetRef
        val profileId = loadHooks.profileId()
        loadJob = viewModelScope.launch {
            loadAndApply(app, ref, atSec, profileId, forceRefresh)
        }
    }

    fun onInstantSet(newTimeSec: Int) {
        if (newTimeSec == timeSec) {
            return
        }
        persist(saved.copy(timeSec = newTimeSec.toLong()))
        reload()
    }

    fun pickBouquet() {
        persist(saved.copy(waitingForPicker = true))
        pickBouquetChannel.trySend(Statics.REQUEST_PICK_BOUQUET)
    }

    fun onPickerResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || requestCode != Statics.REQUEST_PICK_BOUQUET) {
            return
        }
        val service = data?.getSerializableExtraCompat<Service>(KEY_BOUQUET) ?: return
        val reference = service.reference
        if (reference != bouquetRef) {
            persist(
                saved.copy(
                    bouquetRef = reference,
                    bouquetName = service.name,
                    waitingForPicker = false
                )
            )
            listState.scrollToTop()
        } else {
            persist(saved.copy(waitingForPicker = false))
        }
        reload()
    }

    private fun seedIfAbsent(leafRef: String, leafName: String, leafTimeSec: Long?, nowSec: Int) {
        val refAbsent = savedAccess.getBouquetRef() == null
        val timeAbsent = savedAccess.getTimeSec() == null
        if (!refAbsent && !timeAbsent) {
            return
        }
        persist(
            saved.copy(
                bouquetRef = if (refAbsent) leafRef else saved.bouquetRef,
                bouquetName = if (refAbsent) leafName else saved.bouquetName,
                timeSec = if (timeAbsent) {
                    resolvedTimeSec(leafTimeSec, nowSec)
                } else {
                    saved.timeSec
                }
            )
        )
    }

    private fun resolvedTimeSec(leafTimeSec: Long?, nowSec: Int): Long =
        (leafTimeSec?.toInt() ?: nowSec).toLong()

    private suspend fun loadAndApply(
        app: Application,
        ref: String,
        atSec: Int,
        profileId: Int?,
        forceRefresh: Boolean
    ) {
        val hadCache = if (!forceRefresh) {
            applyCachedEvents(app, ref, atSec, profileId)
        } else {
            false
        }
        if (!coroutineContext.isActive) {
            return
        }
        if (!forceRefresh && loadHooks.shouldSkipReceiverHttp(hadCache)) {
            return
        }
        val result = loadHooks.loadEvents(
            app,
            listOf(
                NameValuePair("bRef", ref),
                NameValuePair("time", atSec.toString())
            )
        )
        if (!coroutineContext.isActive) {
            return
        }
        if (result.success) {
            applyEvents(app, result.events)
            return
        }
        if (applyCachedEvents(app, ref, atSec, profileId)) {
            return
        }
        if (!coroutineContext.isActive) {
            return
        }
        refresh.setRefreshing(false)
        listState.replaceAll(emptyList())
        emptyMessage = result.errorText
    }

    private suspend fun applyCachedEvents(
        app: Application,
        ref: String,
        atSec: Int,
        profileId: Int?
    ): Boolean {
        if (profileId == null) {
            return false
        }
        val cached = ListEpgCache.loadBouquetEvents(
            loadHooks.epgDao(app),
            profileId,
            ref,
            atSec.toLong()
        ) ?: return false
        if (!coroutineContext.isActive) {
            return false
        }
        applyEvents(app, cached)
        return true
    }

    private fun applyEvents(app: Application, events: List<Event>) {
        refresh.setRefreshing(false)
        if (events.isEmpty()) {
            listState.replaceAll(emptyList())
            emptyMessage = app.getString(R.string.no_list_item)
        } else {
            emptyMessage = null
            listState.replaceAll(events)
        }
    }

    private fun persist(next: EpgBouquetNavSaved) {
        saved = next
        next.writeTo(savedAccess)
        bouquetRef = next.bouquetRef
        bouquetName = next.bouquetName
        waitingForPicker = next.waitingForPicker
        timeSec = next.timeSec?.toInt()
    }
}

internal class EpgBouquetLoadHooks(
    val profileId: () -> Int? = { DreamDroid.getCurrentProfile().id },
    val epgDao: (Context) -> EpgDao = { context -> AppDatabase.epg(context) },
    val shouldSkipReceiverHttp: (Boolean) -> Boolean = { hasCache ->
        SessionConnectionHolder.shared.status.value.shouldSkipReceiverHttp(hasCache)
    },
    val loadEvents: suspend (
        Context,
        List<NameValuePair>
    ) -> EventListLoadResult = { context, params ->
        loadEventList(context, params, URIStore.EPG_BOUQUET)
    }
)

private class HandleEpgBouquetNavSavedAccess(private val handle: SavedStateHandle) :
    EpgBouquetNavSavedAccess {
    override fun getBouquetRef(): String? = handle.get<String>(EpgBouquetNavSavedKeys.BOUQUET_REF)

    override fun setBouquetRef(bouquetRef: String) {
        handle[EpgBouquetNavSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? = handle.get<String>(EpgBouquetNavSavedKeys.BOUQUET_NAME)

    override fun setBouquetName(bouquetName: String) {
        handle[EpgBouquetNavSavedKeys.BOUQUET_NAME] = bouquetName
    }

    override fun getTimeSec(): Long? = handle.get<Long>(EpgBouquetNavSavedKeys.TIME_SEC)

    override fun setTimeSec(timeSec: Long?) {
        if (timeSec == null) {
            handle.remove<Long>(EpgBouquetNavSavedKeys.TIME_SEC)
        } else {
            handle[EpgBouquetNavSavedKeys.TIME_SEC] = timeSec
        }
    }

    override fun getWaitingForPicker(): Boolean? =
        handle.get<Boolean>(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER)

    override fun setWaitingForPicker(waitingForPicker: Boolean) {
        handle[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
    }
}
