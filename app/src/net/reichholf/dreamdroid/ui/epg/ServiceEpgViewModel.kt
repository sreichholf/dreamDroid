package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgDao
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.nav.ServiceEpg
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Owns [ServiceEpgListState], refresh, the empty message, and the list load for one
 * service EPG back-stack entry. The service comes from the route arguments.
 * The load stays on [viewModelScope] so opening an event and popping back keeps the list.
 */
class ServiceEpgViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val route: ServiceEpg = savedStateHandle.toRoute()
    val serviceRef: String = route.serviceRef
    val serviceName: String = route.serviceName

    val listState: ServiceEpgListState = ServiceEpgListState()
    val refresh: ComposeRefreshState = ComposeRefreshState()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    internal var loadHooks: ServiceEpgLoadHooks = ServiceEpgLoadHooks()

    private var boundSession: ConnectionStatus.Session? = null
    private var bound = false
    private var loadJob: Job? = null

    /** Loads on the first bind and when the connection session changes, not on re-entry. */
    fun bindSession(session: ConnectionStatus.Session?) {
        if (bound && session == boundSession) {
            return
        }
        bound = true
        boundSession = session
        reload()
    }

    fun reload(forceRefresh: Boolean = false) {
        if (serviceRef.isEmpty()) {
            return
        }
        val app = getApplication<Application>()
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadAndApply(app, forceRefresh)
        }
    }

    private suspend fun loadAndApply(app: Application, forceRefresh: Boolean) {
        val profileId = loadHooks.profileId()
        val nowSec = System.currentTimeMillis() / 1000L
        suspend fun paintCache(): Boolean {
            if (profileId == null) {
                return false
            }
            val cached = ListEpgCache.loadServiceEvents(
                loadHooks.epgDao(app),
                profileId,
                serviceRef,
                nowSec
            ) ?: return false
            applyEvents(app, cached)
            return true
        }
        val hadCache = if (!forceRefresh) {
            paintCache()
        } else {
            false
        }
        if (!forceRefresh && loadHooks.shouldSkipReceiverHttp(hadCache)) {
            return
        }
        val result = loadHooks.loadEvents(app, listOf(NameValuePair("sRef", serviceRef)))
        if (result.success) {
            applyEvents(app, result.events)
            return
        }
        if (paintCache()) {
            return
        }
        refresh.setRefreshing(false)
        listState.replaceAll(emptyList())
        emptyMessage = result.errorText
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
}

internal class ServiceEpgLoadHooks(
    val profileId: () -> Int? = { DreamDroid.getCurrentProfile().id },
    val epgDao: (Context) -> EpgDao = { context -> AppDatabase.epg(context) },
    val shouldSkipReceiverHttp: (Boolean) -> Boolean = { hasCache ->
        SessionConnectionHolder.shared.status.value.shouldSkipReceiverHttp(hasCache)
    },
    val loadEvents: suspend (
        Context,
        List<NameValuePair>
    ) -> EventListLoadResult = { context, params ->
        loadEventList(context, params, URIStore.EPG_SERVICE)
    }
)
