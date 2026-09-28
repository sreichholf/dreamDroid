package net.reichholf.dreamdroid.ui.zap

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceListLoad
import net.reichholf.dreamdroid.data.serviceRepository
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentError
import net.reichholf.dreamdroid.enigma.userMessage
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET

/**
 * Owns one [ZapListState], the saved bouquet, and the load/zap jobs.
 * Jobs stay on [viewModelScope] so leaving the destination does not cancel them.
 */
class ZapViewModel(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val listState: ZapListState = ZapListState()
    val refresh: ComposeRefreshState = ComposeRefreshState()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var toolbarTitle by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    private val pickBouquetChannel = Channel<Int>(Channel.CONFLATED)
    private val streamChannel = Channel<Service>(Channel.CONFLATED)
    val pickBouquetRequests: Flow<Int> = pickBouquetChannel.receiveAsFlow()
    val streamRequests: Flow<Service> = streamChannel.receiveAsFlow()

    private var saved: ZapNavSaved
    private var started = false
    private var loadJob: Job? = null
    private var zapJob: Job? = null

    init {
        val profile = ProfileRepository.get().requireCurrent()
        saved = readZapNavSaved(
            savedStateHandle,
            defaultBouquetRef = profile.defaultBouquetTv.orEmpty(),
            defaultBouquetName = profile.defaultBouquetTvName.orEmpty()
        )
        toolbarTitle = finishedTitle()
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
        if (ZapPickerGate.shouldNavigateToPickBouquet(saved.bouquetRef, saved.waitingForPicker)) {
            pickBouquet()
            return
        }
        if (saved.bouquetRef.isEmpty()) {
            return
        }
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refresh.setRefreshing(true)
        toolbarTitle = app.getString(R.string.loading)
        loadJob?.cancel()
        val bouquetRef = saved.bouquetRef
        loadJob = viewModelScope.launch {
            val load = serviceRepository(app).services(bouquetRef)
            if (!isActive) {
                return@launch
            }
            refresh.setRefreshing(false)
            toolbarTitle = finishedTitle()
            when (load) {
                is ServiceListLoad.Services -> publishRows(ZapListMapper.rowsFrom(load.services))

                is ServiceListLoad.Failed -> {
                    listState.replaceAll(emptyList())
                    emptyMessage = load.error.contentError(app)
                }
            }
        }
    }

    fun zapTo(reference: String) {
        val app = getApplication<Application>()
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            val response = EnigmaClient().zap(listOf(NameValuePair("sRef", reference)))
            if (!isActive) {
                return@launch
            }
            errorText = response.userMessage(app)
        }
    }

    fun requestStream(service: Service) {
        streamChannel.trySend(service)
    }

    fun pickBouquet() {
        persist(saved.copy(waitingForPicker = true))
        pickBouquetChannel.trySend(Statics.REQUEST_PICK_BOUQUET)
    }

    fun onPickerResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!ZapPickerGate.isBouquetPickerRequest(requestCode)) {
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            val effect = ZapPickerGate.afterNonOkPickerResult(listState.items.isEmpty())
            persist(saved.copy(waitingForPicker = effect.waitingForPicker))
            val messageRes = effect.emptyMessageResId
            if (messageRes != null) {
                emptyMessage = getApplication<Application>().getString(messageRes)
            }
            return
        }
        val bouquet = data?.getSerializableExtraCompat<Service>(KEY_BOUQUET) ?: Service("", "")
        val refChanged = bouquet.reference != saved.bouquetRef
        val nextRef = if (refChanged) bouquet.reference else saved.bouquetRef
        val nextName = if (refChanged) bouquet.name else saved.bouquetName
        persist(
            saved.copy(
                bouquetRef = nextRef,
                bouquetName = nextName,
                waitingForPicker = false
            )
        )
        if (refChanged) {
            listState.scrollToTop()
        }
        reload()
    }

    fun reportMissingStreamPlayer() {
        val app = getApplication<Application>()
        errorText = app.getText(R.string.missing_stream_player).toString()
    }

    fun consumeError() {
        errorText = null
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

    private fun finishedTitle(): String {
        val app = getApplication<Application>()
        return saved.bouquetName.takeIf { it.isNotEmpty() } ?: app.getString(R.string.app_name)
    }

    private fun persist(next: ZapNavSaved) {
        saved = next
        next.writeTo(savedStateHandle)
    }
}
