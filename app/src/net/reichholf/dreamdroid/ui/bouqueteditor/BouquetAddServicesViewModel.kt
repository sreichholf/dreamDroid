package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetEditorRepository
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.BouquetMode
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.nav.BouquetAddServices
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Where services to add come from. */
enum class ServiceSource(@param:StringRes val label: Int) {
    Satellites(R.string.bouquet_source_satellites),
    Providers(R.string.bouquet_source_providers),
    All(R.string.bouquet_source_all)
}

sealed interface AddServicesList {
    data object Loading : AddServicesList

    data class Failed(val message: UiText) : AddServicesList

    /** Folders to open and services to pick; markers and alternatives are left out. */
    data class Ready(val entries: List<BouquetEntry>) : AddServicesList
}

/**
 * The service picker for [bouquetRef]. With no [source] it offers the sources; else it
 * lists the source, or the folders opened under it ([path]). [present] are the references
 * the bouquet has already; [selected] are the picked ones of the list on screen.
 * [finished] is the outcome to show once this destination has left. [present] holds
 * [presenceKey]s: a renamed entry keeps its service, not its reference. [closed] once the
 * active profile changed: the bouquet belongs to the old receiver, so the picker leaves.
 */
data class BouquetAddServicesUiState(
    val bouquetRef: String,
    val mode: BouquetMode,
    val source: ServiceSource? = null,
    val path: List<BouquetEntry> = emptyList(),
    val content: AddServicesList = AddServicesList.Loading,
    val present: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val blocked: Boolean = false,
    val pending: Boolean = false,
    val userMessage: UiText? = null,
    val finished: UiText? = null,
    val closed: Boolean = false
) {
    val title: UiText
        get() {
            val folder = path.lastOrNull()
            return when {
                folder != null -> UiText.Raw(folder.name)
                source != null -> UiText.Resource(source.label)
                else -> UiText.Resource(R.string.bouquet_add_services)
            }
        }

    /** Whether the add action takes the selection now. */
    val canAdd: Boolean
        get() = selected.isNotEmpty() && !blocked && !pending && !closed

    /** Whether [entry] can be picked: a service or stream the bouquet does not have yet. */
    fun selectable(entry: BouquetEntry): Boolean =
        entry.isService && presenceKey(entry.reference) !in present
}

/**
 * Picks services from satellites, providers, or all services and appends them to the
 * [BouquetAddServices] route's bouquet with one [BouquetEditorRepository.addServices] call.
 * A request the box did not answer keeps the picker open; any answer finishes it.
 */
@HiltViewModel
class BouquetAddServicesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val editor: BouquetEditorRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BouquetAddServicesUiState(
            bouquetRef = savedStateHandle.get<String>(BouquetAddServices::bouquetRef.name)
                .orEmpty(),
            mode = savedStateHandle.get<String>(BouquetAddServices::mode.name)
                ?.let { saved -> BouquetMode.entries.firstOrNull { it.name == saved } }
                ?: BouquetMode.Tv,
            source = savedStateHandle.get<String>(KEY_SOURCE)
                ?.let { saved -> ServiceSource.entries.firstOrNull { it.name == saved } },
            path = savedPath(savedStateHandle),
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<BouquetAddServicesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The receiver the bouquet belongs to. */
    private val receiver = editor.currentReceiver()

    init {
        viewModelScope.launch { editor.receiver.collect { receiverChanged() } }
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
            }
        }
        loadPresent()
        reload()
    }

    /** Loads the list on screen again; the source choice has nothing to load. */
    fun reload() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.closed) {
            return
        }
        loadJob?.cancel()
        _uiState.update { it.copy(content = AddServicesList.Loading) }
        val folder = state.path.lastOrNull()
        loadJob = viewModelScope.launch {
            val response = when {
                folder != null -> editor.entries(folder.reference)
                source == ServiceSource.Satellites -> editor.satellites(state.mode)
                source == ServiceSource.Providers -> editor.providers(state.mode)
                else -> editor.allServices(state.mode)
            }
            val content = response.value
                ?.let { entries -> AddServicesList.Ready(entries.filter { it.isListed }) }
                ?: AddServicesList.Failed(response.error.contentErrorText())
            _uiState.update { it.copy(content = content) }
        }
    }

    fun openSource(source: ServiceSource) {
        show(source, emptyList())
    }

    fun openFolder(folder: BouquetEntry) {
        val state = _uiState.value
        if (folder.kind != BouquetEntryKind.Directory || state.source == null) {
            return
        }
        show(state.source, state.path + folder)
    }

    /** Back one level. False when the sources are on screen. */
    fun navigateUp(): Boolean {
        val state = _uiState.value
        when {
            state.pending -> Unit
            state.path.isNotEmpty() -> show(state.source, state.path.dropLast(1))
            state.source != null -> show(null, emptyList())
            else -> return false
        }
        return true
    }

    fun toggle(entry: BouquetEntry) {
        val state = _uiState.value
        if (state.pending || !state.selectable(entry)) {
            return
        }
        val ref = entry.reference
        _uiState.update {
            it.copy(selected = if (ref in it.selected) it.selected - ref else it.selected + ref)
        }
    }

    /** Appends the picked services to the bouquet, in list order. */
    fun add() {
        val state = _uiState.value
        val entries = (state.content as? AddServicesList.Ready)?.entries ?: return
        if (receiverChanged() || !state.canAdd) {
            return
        }
        val refs = entries.map { it.reference }.filter { it in state.selected }
        if (refs.isEmpty()) {
            return
        }
        loadJob?.cancel()
        _uiState.update { it.copy(pending = true) }
        viewModelScope.launch {
            val result = editor.addServices(state.bouquetRef, refs)
            if (result.response.value == null) {
                // Some may have reached the box before it stopped answering.
                val present = editor.entries(state.bouquetRef).presentRefs()
                _uiState.update {
                    it.copy(
                        pending = false,
                        present = present ?: it.present,
                        selected = it.selected.withoutPresent(present),
                        userMessage = result.response.userMessageText()
                    )
                }
                return@launch
            }
            val outcome = result.userMessage()
                ?: UiText.Resource(R.string.bouquet_services_added, listOf(refs.size))
            // A closed picker leaves on its own.
            _uiState.update { if (it.closed) it else it.copy(pending = false, finished = outcome) }
        }
    }

    fun onFinishHandled() {
        _uiState.update { it.copy(finished = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Closes the picker when the active profile's receiver is no longer the bouquet's. */
    private fun receiverChanged(): Boolean {
        if (editor.currentReceiver() == receiver) {
            return false
        }
        loadJob?.cancel()
        _uiState.update { it.copy(closed = true, pending = false) }
        return true
    }

    private fun show(source: ServiceSource?, path: List<BouquetEntry>) {
        if (_uiState.value.pending) {
            return
        }
        loadJob?.cancel()
        savedStateHandle[KEY_SOURCE] = source?.name
        savedStateHandle[KEY_PATH_REFS] = ArrayList(path.map { it.reference })
        savedStateHandle[KEY_PATH_NAMES] = ArrayList(path.map { it.name })
        _uiState.update {
            it.copy(
                source = source,
                path = path,
                content = AddServicesList.Loading,
                selected = emptySet()
            )
        }
        reload()
    }

    private fun loadPresent() {
        viewModelScope.launch {
            val present = editor.entries(_uiState.value.bouquetRef).presentRefs() ?: return@launch
            _uiState.update {
                it.copy(present = present, selected = it.selected.withoutPresent(present))
            }
        }
    }

    private companion object {
        const val KEY_SOURCE = "bouquet_add_source"
        const val KEY_PATH_REFS = "bouquet_add_path_refs"
        const val KEY_PATH_NAMES = "bouquet_add_path_names"

        fun savedPath(handle: SavedStateHandle): List<BouquetEntry> {
            val refs = handle.get<ArrayList<String>>(KEY_PATH_REFS).orEmpty()
            val names = handle.get<ArrayList<String>>(KEY_PATH_NAMES).orEmpty()
            return refs.zip(names) { ref, name ->
                BouquetEntry(ref, name, BouquetEntryKind.Directory)
            }
        }
    }
}

private val BouquetEntry.isService: Boolean
    get() = kind == BouquetEntryKind.Service || kind == BouquetEntryKind.Stream

/** Folders to open and services to pick. */
private val BouquetEntry.isListed: Boolean
    get() = isService || kind == BouquetEntryKind.Directory

/**
 * [ref] without the name a rename appends: type to path. The bouquet's `…:0:0:0::New Name`
 * and the source's `…:0:0:0:` are the same service.
 */
internal fun presenceKey(ref: String): String = ref.split(':').take(REF_FIELDS).joinToString(":")

private const val REF_FIELDS = 11

private fun EnigmaResponse<List<BouquetEntry>>.presentRefs(): Set<String>? =
    value?.mapTo(HashSet()) { presenceKey(it.reference) }

private fun Set<String>.withoutPresent(present: Set<String>?): Set<String> =
    if (present == null) this else filterNot { presenceKey(it) in present }.toSet()
