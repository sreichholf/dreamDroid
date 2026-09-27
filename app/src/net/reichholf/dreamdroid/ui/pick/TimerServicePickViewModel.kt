package net.reichholf.dreamdroid.ui.pick

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * Owns one [TimerServicePickSession]. Bouquet ref and name are the only saved fields.
 * Load jobs stay on [viewModelScope] and are not cancelled when the composable leaves.
 */
class TimerServicePickViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val savedAccess = HandleTimerServicePickSavedAccess(savedStateHandle)
    val session: TimerServicePickSession

    private var started = false

    init {
        session = TimerServicePickSession(
            app = application,
            scope = viewModelScope,
            initial = readTimerServicePickSaved(savedAccess),
            persist = { next -> next.writeTo(savedAccess) }
        )
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        session.reload()
    }
}

/**
 * Bouquet then channel list. [PickServiceListState] stays the row model.
 * The composable delivers a picked channel; this helper does not hold the activity.
 */
class TimerServicePickSession(
    private val app: Application,
    private val scope: CoroutineScope,
    initial: TimerServicePickSaved,
    private val persist: (TimerServicePickSaved) -> Unit
) {
    val listState: PickServiceListState = PickServiceListState()
    val refresh: ComposeRefreshState = ComposeRefreshState()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var toolbarTitle by mutableStateOf("")
        private set

    var bouquetRef by mutableStateOf(initial.bouquetRef)
        private set

    var bouquetName by mutableStateOf(initial.bouquetName)
        private set

    private var bouquets: List<Service> = emptyList()
    private var loadJob: Job? = null
    private var loadGeneration = 0

    init {
        toolbarTitle = finishedTitle()
    }

    fun reload() {
        if (bouquetRef.isEmpty()) {
            loadBouquets()
        } else {
            loadServices()
        }
    }

    fun showBouquetList() {
        updateSaved(TimerServicePickSaved(bouquetRef = "", bouquetName = ""))
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
        if (bouquets.isNotEmpty()) {
            refresh.setRefreshing(false)
            emptyMessage = null
            listState.replaceAll(bouquets)
            toolbarTitle = app.getString(R.string.service)
        } else {
            listState.replaceAll(emptyList())
            loadBouquets()
        }
    }

    /**
     * Bouquet rows open that bouquet. A channel row is returned so the composable can deliver
     * the activity result. Markers are ignored.
     */
    fun onRowClick(service: Service): Service? {
        if (ServiceKeys.isMarker(service.reference)) {
            return null
        }
        if (bouquetRef.isEmpty()) {
            updateSaved(
                TimerServicePickSaved(
                    bouquetRef = service.reference,
                    bouquetName = service.name
                )
            )
            listState.replaceAll(emptyList())
            emptyMessage = app.getString(R.string.loading)
            loadServices()
            return null
        }
        return service
    }

    private fun loadBouquets() {
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        toolbarTitle = app.getString(R.string.loading)
        loadJob?.cancel()
        loadGeneration++
        val generation = loadGeneration
        loadJob = scope.launch {
            val result = loadBouquetList(app)
            if (!isActive || generation != loadGeneration || bouquetRef.isNotEmpty()) {
                return@launch
            }
            refresh.setRefreshing(false)
            toolbarTitle = app.getString(R.string.service)
            if (!result.success) {
                val profileId = ProfileRepository.get().requireCurrent().id
                val cached = if (profileId != null) {
                    val dao = AppDatabase.roster(app)
                    val cachedRows = ArrayList(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_TV
                        )
                    )
                    cachedRows.addAll(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_RADIO
                        )
                    )
                    cachedRows
                } else {
                    emptyList()
                }
                if (!isActive || generation != loadGeneration || bouquetRef.isNotEmpty()) {
                    return@launch
                }
                if (cached.isNotEmpty()) {
                    bouquets = cached
                    emptyMessage = null
                    listState.replaceAll(cached)
                    return@launch
                }
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText ?: app.getString(R.string.no_list_item)
                return@launch
            }
            val rows = ArrayList(result.bouquets.tv)
            rows.addAll(result.bouquets.radio)
            bouquets = rows
            publishRows(rows)
        }
    }

    private fun loadServices() {
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        toolbarTitle = app.getString(R.string.loading)
        loadJob?.cancel()
        loadGeneration++
        val generation = loadGeneration
        val ref = bouquetRef
        val title = bouquetName.ifEmpty { app.getString(R.string.service) }
        loadJob = scope.launch {
            val result = loadServiceList(
                app,
                listOf(NameValuePair("sRef", ref))
            )
            if (!isActive || generation != loadGeneration || bouquetRef.isEmpty()) {
                return@launch
            }
            refresh.setRefreshing(false)
            toolbarTitle = title
            if (!result.success) {
                val profileId = ProfileRepository.get().requireCurrent().id
                val cached = if (profileId != null) {
                    UserBouquetCache.loadRosterServices(
                        AppDatabase.roster(app),
                        profileId,
                        ref
                    )
                } else {
                    null
                }
                if (!isActive || generation != loadGeneration || bouquetRef.isEmpty()) {
                    return@launch
                }
                if (cached != null) {
                    publishRows(ZapListMapper.rowsFrom(cached))
                    return@launch
                }
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText ?: app.getString(R.string.no_list_item)
                return@launch
            }
            publishRows(ZapListMapper.rowsFrom(result.services))
        }
    }

    private fun publishRows(rows: List<Service>) {
        if (rows.isEmpty()) {
            listState.replaceAll(emptyList())
            emptyMessage = app.getString(R.string.no_list_item)
        } else {
            emptyMessage = null
            listState.replaceAll(rows)
        }
    }

    private fun finishedTitle(): String = if (bouquetRef.isEmpty()) {
        app.getString(R.string.service)
    } else {
        bouquetName.ifEmpty { app.getString(R.string.service) }
    }

    private fun updateSaved(next: TimerServicePickSaved) {
        bouquetRef = next.bouquetRef
        bouquetName = next.bouquetName
        persist(next)
    }
}

private class HandleTimerServicePickSavedAccess(private val handle: SavedStateHandle) :
    TimerServicePickSavedAccess {
    override fun getBouquetRef(): String? =
        handle.get<String>(TimerServicePickSavedKeys.BOUQUET_REF)

    override fun setBouquetRef(bouquetRef: String) {
        handle[TimerServicePickSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? =
        handle.get<String>(TimerServicePickSavedKeys.BOUQUET_NAME)

    override fun setBouquetName(bouquetName: String) {
        handle[TimerServicePickSavedKeys.BOUQUET_NAME] = bouquetName
    }
}
