package net.reichholf.dreamdroid.ui.services

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState

/**
 * Hub timer list for [HubTimerListPage], scoped to the hub back-stack entry.
 * [HubTimerListSession] stays the list model and the menu provider.
 * The same remount epoch does not load again, so returning to the tab keeps the list.
 */
class HubTimerListViewModel(application: Application) : AndroidViewModel(application) {
    val session: HubTimerListSession = HubTimerListSession()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    private var appliedEpoch: String? = null

    init {
        session.listState = TimerListState()
        session.refresh = ComposeRefreshState()
        session.scope = viewModelScope
        session.onEmptyMessage = { emptyMessage = it }
    }

    fun onRemount(epoch: Int) {
        val key = epoch.toString()
        if (!shouldLoadHubPage(appliedEpoch, key)) {
            return
        }
        appliedEpoch = key
        session.reload()
    }

    override fun onCleared() {
        session.cancelInFlight()
        super.onCleared()
    }
}
