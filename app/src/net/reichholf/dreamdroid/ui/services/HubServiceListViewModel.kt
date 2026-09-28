package net.reichholf.dreamdroid.ui.services

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState

/**
 * One bouquet's service list for [HubServiceListPage].
 * Key this ViewModel by bouquet ref on the hub back-stack entry so a tab
 * change keeps the loaded list and the directory drill-down.
 * [HubServiceListSession] stays the list model and the menu provider.
 */
class HubServiceListViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val session: HubServiceListSession = HubServiceListSession()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var historyDepth by mutableIntStateOf(0)
        private set

    private var rootRef: String = ""
    private var rootBound: Boolean = false
    private var connectionKey: String? = null

    init {
        session.listState = ServiceListState()
        session.refresh = ComposeRefreshState()
        session.rows = mutableListOf()
        session.history = mutableListOf()
        session.scope = viewModelScope
        session.onEmptyMessage = { emptyMessage = it }
        session.onHistoryDepth = { historyDepth = it }
        session.onCurrentRef = { ref -> savedStateHandle[hubServiceCurrentRefKey(rootRef)] = ref }
        session.onCurrentName =
            { name -> savedStateHandle[hubServiceCurrentNameKey(rootRef)] = name }
    }

    fun bindRoot(bouquetRef: String, bouquetName: String) {
        if (rootBound) {
            return
        }
        rootBound = true
        rootRef = bouquetRef
        session.rootRef = bouquetRef
        session.rootName = bouquetName
        val saved = readHubServiceListSaved(savedStateHandle, bouquetRef)
        val restored = restoreHubServiceDrillDown(
            bouquetRef,
            bouquetName,
            saved.currentRef,
            saved.currentName
        )
        restored.historyStep?.let { step ->
            session.history?.add(step)
            historyDepth = session.history?.size ?: 0
        }
        session.currentRef = restored.currentRef
        session.currentName = restored.currentName
    }

    fun onConnection(connectionName: String) {
        if (!shouldLoadHubPage(connectionKey, connectionName)) {
            return
        }
        connectionKey = connectionName
        session.reload()
    }

    override fun onCleared() {
        session.cancelInFlight()
    }
}
