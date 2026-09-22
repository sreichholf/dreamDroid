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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.enigma.simpleResultFromFetch
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.pick.KEY_BOUQUET

/**
 * Owns one [ZapListState], the saved bouquet, and the load/zap jobs.
 * Jobs stay on [viewModelScope] so leaving the destination does not cancel them.
 */
class ZapViewModel(application: Application, savedStateHandle: SavedStateHandle) :
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

    private val savedAccess = HandleZapNavSavedAccess(savedStateHandle)
    private var saved: ZapNavSaved
    private var started = false
    private var loadJob: Job? = null
    private var zapJob: Job? = null

    init {
        val profile = DreamDroid.getCurrentProfile()
        saved = readZapNavSaved(
            savedAccess,
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
            val params = listOf(NameValuePair("sRef", bouquetRef))
            val result = loadServiceList(app, params)
            if (!isActive) {
                return@launch
            }
            refresh.setRefreshing(false)
            toolbarTitle = finishedTitle()
            if (!result.success) {
                val profileId = DreamDroid.getCurrentProfile().id
                val cached = if (profileId != null) {
                    UserBouquetCache.loadRosterServices(
                        AppDatabase.roster(app),
                        profileId,
                        bouquetRef
                    )
                } else {
                    null
                }
                if (!isActive) {
                    return@launch
                }
                if (cached != null) {
                    publishRows(ZapListMapper.rowsFrom(cached))
                    return@launch
                }
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText
                return@launch
            }
            publishRows(ZapListMapper.rowsFrom(result.services))
        }
    }

    fun zapTo(reference: String) {
        val app = getApplication<Application>()
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            val handler = ZapRequestHandler()
            val params = listOf(NameValuePair("sRef", reference))
            val outcome = withContext(Dispatchers.IO) {
                simpleResultFromFetch(handler.fetch(EnigmaHttp(), params)) { xml ->
                    handler.parseSimpleResult(xml)
                }
            }
            if (!isActive) {
                return@launch
            }
            val result = outcome.second
            val error = outcome.third
            var toastText = app.getText(R.string.get_content_error).toString()
            val stateText = result.stateText
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                error != null -> toastText = error.resolve(app).orEmpty()
            }
            errorText = toastText
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
        next.writeTo(savedAccess)
    }
}

private class HandleZapNavSavedAccess(private val handle: SavedStateHandle) : ZapNavSavedAccess {
    override fun getBouquetRef(): String? = handle.get<String>(ZapNavSavedKeys.BOUQUET_REF)

    override fun setBouquetRef(bouquetRef: String) {
        handle[ZapNavSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? = handle.get<String>(ZapNavSavedKeys.BOUQUET_NAME)

    override fun setBouquetName(bouquetName: String) {
        handle[ZapNavSavedKeys.BOUQUET_NAME] = bouquetName
    }

    override fun getWaitingForPicker(): Boolean? =
        handle.get<Boolean>(ZapNavSavedKeys.WAITING_FOR_PICKER)

    override fun setWaitingForPicker(waitingForPicker: Boolean) {
        handle[ZapNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
    }
}
