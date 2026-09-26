package net.reichholf.dreamdroid.ui.pick

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState

/**
 * Bouquet list for [PickServiceDestination]. [PickServiceListState] stays the list model.
 *
 * [savedStateHandle] is accepted so the default factory can construct this ViewModel.
 * This screen has no rememberSaveable fields, so no keys are written.
 * The load job stays on [viewModelScope] and is not cancelled when the composable leaves.
 */
class PickServiceViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val listState: PickServiceListState = PickServiceListState()
    val refresh: ComposeRefreshState = ComposeRefreshState()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var toolbarTitle by mutableStateOf("")
        private set

    private var started = false
    private var loadJob: Job? = null

    init {
        toolbarTitle = getApplication<Application>().getString(R.string.services)
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        reload()
    }

    fun reload() {
        val app = getApplication<Application>()
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        toolbarTitle = app.getString(R.string.loading)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadBouquetList(app)
            if (!isActive) {
                return@launch
            }
            refresh.setRefreshing(false)
            toolbarTitle = app.getString(R.string.services)
            if (!result.success) {
                val profileId = ProfileRepository.get().requireCurrent().id
                val cached = if (profileId != null) {
                    val dao = AppDatabase.roster(app)
                    val rows = ArrayList(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_TV
                        )
                    )
                    rows.addAll(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_RADIO
                        )
                    )
                    rows
                } else {
                    emptyList()
                }
                if (!isActive) {
                    return@launch
                }
                if (cached.isNotEmpty()) {
                    listState.replaceAll(cached)
                    emptyMessage = null
                    return@launch
                }
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText
                return@launch
            }
            val rows = ArrayList(result.bouquets.tv)
            rows.addAll(result.bouquets.radio)
            publishRows(rows)
        }
    }

    private fun publishRows(rows: List<Service>) {
        val app = getApplication<Application>()
        if (rows.isEmpty()) {
            listState.replaceAll(emptyList())
            emptyMessage = app.getString(R.string.no_list_item)
        } else {
            emptyMessage = null
            listState.replaceAll(rows)
        }
    }
}
