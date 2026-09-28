package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.NowNextListLoad
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Something the page does outside this ViewModel, then reports with `onEffectHandled`. */
sealed interface HubServiceEffect {
    /** Show [event] in the EPG detail sheet. */
    data class ShowEvent(val event: Event) : HubServiceEffect

    /** Open the EPG of one service. */
    data class ServiceEpg(val reference: String, val name: String) : HubServiceEffect

    /** Stream [row], a channel of the list [bouquetRef]. */
    data class Stream(val row: ServiceNowNext, val bouquetRef: String) : HubServiceEffect

    /** A zap finished; the hub refreshes what is playing. */
    data object Zapped : HubServiceEffect
}

/**
 * One TV/radio hub bouquet tab. [currentRef] is the list on screen: the tab itself, or a
 * folder opened under it ([historyDepth] levels deep). [emptyMessage] is shown instead of
 * the list when [items] is empty.
 */
data class HubServiceListUiState(
    val currentRef: String,
    val currentName: String,
    val historyDepth: Int = 0,
    val items: List<ServiceListItem> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val menu: RowMenuState<ServiceRowAction>? = null,
    val isDefaultBouquet: Boolean = false,
    val effect: HubServiceEffect? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = when {
            refreshing -> UiText.Resource(R.string.loading)
            currentName.isNotEmpty() -> UiText.Raw(currentName)
            else -> UiText.Resource(R.string.services)
        }
}

/**
 * The service list of one hub bouquet tab. Keyed by the tab's ref on the hub back-stack
 * entry, so a tab change keeps the loaded list and the opened folder; the folder survives
 * process death with one back step to the tab. Loads follow the session: each change of the
 * connection loads again. A live answer fills the Room EPG chunk of a cacheable list.
 */
@HiltViewModel(assistedFactory = HubServiceListViewModel.Factory::class)
class HubServiceListViewModel @AssistedInject constructor(
    @Assisted private val bouquet: Service,
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository,
    private val epg: EpgRepository,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(bouquet: Service): HubServiceListViewModel
    }

    private val rootRef: String = bouquet.reference
    private val history = mutableListOf<Pair<String, String>>()
    private val _uiState: MutableStateFlow<HubServiceListUiState>
    val uiState: StateFlow<HubServiceListUiState>

    private var rows: List<ServiceNowNext> = emptyList()
    private var menuRow: ServiceNowNext? = null
    private var loadJob: Job? = null

    init {
        val saved = readHubServiceListSaved(savedStateHandle, rootRef)
        val restored = restoreHubServiceDrillDown(
            rootRef,
            bouquet.name,
            saved.currentRef,
            saved.currentName
        )
        restored.historyStep?.let(history::add)
        _uiState = MutableStateFlow(
            HubServiceListUiState(
                currentRef = restored.currentRef,
                currentName = restored.currentName,
                historyDepth = history.size,
                isDefaultBouquet = isDefault(restored.currentRef)
            )
        )
        uiState = _uiState.asStateFlow()
        viewModelScope.launch {
            sessions.status.map { it.session }.distinctUntilChanged().collect { reload() }
        }
    }

    /**
     * Loads the list on screen. Room paints first unless [forceRefresh]; see
     * [ServiceRepository.nowNextList].
     */
    fun reload(forceRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.items.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        val ref = _uiState.value.currentRef
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            services.nowNextList(ref, rootRef, forceRefresh).collect { load ->
                apply(load)
                if (load is NowNextListLoad.Rows && !load.cached) {
                    fillEpg(ref)
                }
            }
        }
    }

    /** Opens the folder at [index]. */
    fun openDirectory(index: Int) {
        val row = rows.getOrNull(index) ?: return
        val state = _uiState.value
        history.add(state.currentRef to state.currentName)
        show(row.serviceReference, row.serviceName)
    }

    /** Back one folder level. False when the tab itself is on screen. */
    fun navigateUp(): Boolean {
        val (ref, name) = history.removeLastOrNull() ?: return false
        show(ref, name)
        return true
    }

    /** Tab reselect: back to the tab when a folder is open, else reload. */
    fun upOrReload() {
        if (history.isEmpty()) {
            reload()
            return
        }
        history.clear()
        show(rootRef, bouquet.name)
    }

    /** Opens the row menu of the channel at [index]; Next event only when there is one. */
    fun onItemMenu(index: Int) {
        val row = rows.getOrNull(index) ?: return
        val item = _uiState.value.items.getOrNull(index) ?: return
        menuRow = row
        val actions = ServiceRowAction.entries.filter {
            it != ServiceRowAction.NextEvent || (DreamDroid.featureNowNext() && row.next != null)
        }
        _uiState.update { it.copy(menu = RowMenuState(serviceRowKey(item), actions)) }
    }

    fun onMenuDismiss() {
        _uiState.update { it.copy(menu = null) }
    }

    /** Zaps to the channel at [index]. Ignored while mutations are blocked. */
    fun zap(index: Int) {
        val row = rows.getOrNull(index) ?: return
        zapTo(row.serviceReference)
    }

    /** Online-only actions ([ServiceRowAction.onlineOnly]) do nothing while blocked. */
    fun onMenuAction(action: ServiceRowAction) {
        val row = menuRow ?: return
        _uiState.update { it.copy(menu = null) }
        if (action.onlineOnly && sessions.status.value.blocksMutations) {
            return
        }
        when (action) {
            ServiceRowAction.CurrentEvent -> row.now?.let { effect(HubServiceEffect.ShowEvent(it)) }

            ServiceRowAction.NextEvent -> row.next?.let { effect(HubServiceEffect.ShowEvent(it)) }

            ServiceRowAction.BrowseEpg ->
                effect(HubServiceEffect.ServiceEpg(row.serviceReference, row.serviceName))

            ServiceRowAction.Zap -> zapTo(row.serviceReference)

            ServiceRowAction.Stream ->
                effect(HubServiceEffect.Stream(row, _uiState.value.currentRef))
        }
    }

    fun onEffectHandled() {
        _uiState.update { it.copy(effect = null) }
    }

    /** No app could play the stream. */
    fun onStreamFailed() {
        _uiState.update {
            it.copy(effect = null, userMessage = UiText.Resource(R.string.missing_stream_player))
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Makes the list on screen the profile's default bouquet, or resets it when it is. */
    fun toggleDefaultBouquet() {
        val state = _uiState.value
        if (state.currentRef.isEmpty()) {
            showMessage(UiText.Resource(R.string.default_bouquet_not_set))
            return
        }
        val profile = profiles.requireCurrent()
        val reset = profile.defaultBouquetTv == state.currentRef
        if (reset) {
            profile.defaultBouquetTv = null
        } else {
            profile.setDefaultRefValues(state.currentRef, state.currentName)
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profiles.save(profile) }
            _uiState.update {
                it.copy(
                    isDefaultBouquet = isDefault(it.currentRef),
                    userMessage = if (reset) {
                        it.userMessage
                    } else {
                        UiText.Resource(
                            R.string.default_bouquet_set_to_name,
                            listOf(
                                UiText.Resource(R.string.default_bouquet_set_to),
                                state.currentName
                            )
                        )
                    }
                )
            }
        }
    }

    private fun show(ref: String, name: String) {
        HubServiceListSaved(currentRef = ref, currentName = name)
            .writeTo(savedStateHandle, rootRef)
        _uiState.update {
            it.copy(
                currentRef = ref,
                currentName = name,
                historyDepth = history.size,
                isDefaultBouquet = isDefault(ref),
                menu = null
            )
        }
        reload()
    }

    private fun apply(load: NowNextListLoad) {
        rows = (load as? NowNextListLoad.Rows)?.rows.orEmpty()
        val emptyMessage = when (load) {
            is NowNextListLoad.Failed -> load.error.contentErrorText()

            is NowNextListLoad.Rows ->
                if (load.rows.isEmpty()) UiText.Resource(R.string.no_list_item) else null
        }
        // Rows changed under an open menu; its row would be stale.
        _uiState.update {
            it.copy(
                items = serviceListItemsFromNowNext(rows),
                refreshing = false,
                emptyMessage = emptyMessage,
                menu = null
            )
        }
    }

    /** Best effort: the list already shows the receiver's now/next. */
    private fun fillEpg(ref: String) {
        viewModelScope.launch {
            try {
                epg.fillNowChunk(ref, rootRef)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun zapTo(reference: String) {
        if (sessions.status.value.blocksMutations) {
            return
        }
        viewModelScope.launch {
            val message = receiver.zap(reference).userMessageText()
            _uiState.update { it.copy(userMessage = message, effect = HubServiceEffect.Zapped) }
        }
    }

    private fun effect(effect: HubServiceEffect) {
        _uiState.update { it.copy(effect = effect) }
    }

    private fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    private fun isDefault(ref: String): Boolean =
        profiles.current.value?.defaultBouquetTv?.let { it == ref } ?: false
}
