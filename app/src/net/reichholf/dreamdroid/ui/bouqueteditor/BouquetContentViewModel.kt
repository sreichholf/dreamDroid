package net.reichholf.dreamdroid.ui.bouqueteditor

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
import net.reichholf.dreamdroid.data.BouquetEditResult
import net.reichholf.dreamdroid.data.BouquetEditorRepository
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.nav.BouquetContent
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

enum class BouquetEntryAction(override val label: Int) : RowMenuAction {
    Rename(R.string.rename),
    InsertMarker(R.string.bouquet_marker_insert_above),
    MoveUp(R.string.move_up),
    MoveDown(R.string.move_down),
    Remove(R.string.remove)
}

/**
 * One row of a bouquet. [key] tells rows apart while they move: a bouquet may hold the same
 * reference twice (spacers).
 */
data class BouquetContentRow(val key: Int, val entry: BouquetEntry)

sealed interface BouquetContentList {
    data object Loading : BouquetContentList

    data class Failed(val message: UiText) : BouquetContentList

    data class Ready(val rows: List<BouquetContentRow>) : BouquetContentList
}

sealed interface BouquetContentDialog {
    data class Rename(val row: BouquetContentRow) : BouquetContentDialog

    /** A new marker before [before], or at the end when null. */
    data class AddMarker(val before: BouquetContentRow?) : BouquetContentDialog

    data class Remove(val row: BouquetContentRow) : BouquetContentDialog
}

/**
 * The entries of one bouquet. [blocked] mirrors the session's `blocksMutations`; [pending]
 * is true while an edit (and the reload after it) runs; [refreshing] while the list loads
 * again, which an edit would cut short. [closed] once the active profile changed: the
 * bouquet belongs to the old receiver, so the destination leaves.
 */
data class BouquetContentUiState(
    val bouquetRef: String,
    val bouquetName: String,
    val mode: BouquetMode,
    val content: BouquetContentList = BouquetContentList.Loading,
    val refreshing: Boolean = false,
    val blocked: Boolean = false,
    val pending: Boolean = false,
    val menu: RowMenuState<BouquetEntryAction>? = null,
    val dialog: BouquetContentDialog? = null,
    val nameError: UiText? = null,
    val userMessage: UiText? = null,
    val closed: Boolean = false
) {
    val title: UiText
        get() = if (bouquetName.isEmpty()) {
            UiText.Resource(R.string.bouquets)
        } else {
            UiText.Raw(bouquetName)
        }

    /** Whether the list takes an edit now. */
    val editable: Boolean
        get() = content is BouquetContentList.Ready &&
            !blocked &&
            !pending &&
            !refreshing &&
            !closed
}

/**
 * The entries of the [BouquetContent] route's bouquet. One edit runs at a time. Moves and
 * removals show at once; renames and new markers change references on the box, so the
 * list loads again after them, as after any edit of an entry whose reference the bouquet
 * holds more than once (the box acts on the first). An edit of this bouquet made elsewhere
 * (adding services) loads the list again too. A profile change closes the list.
 */
@HiltViewModel
class BouquetContentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editor: BouquetEditorRepository,
    private val services: ServiceRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BouquetContentUiState(
            bouquetRef = savedStateHandle.get<String>(BouquetContent::bouquetRef.name).orEmpty(),
            bouquetName = savedStateHandle.get<String>(BouquetContent::bouquetName.name)
                .orEmpty(),
            mode = savedStateHandle.get<String>(BouquetContent::mode.name)
                ?.let { saved -> BouquetMode.entries.firstOrNull { it.name == saved } }
                ?: BouquetMode.Tv,
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<BouquetContentUiState> = _uiState.asStateFlow()

    /** The name in the rename or marker dialog. */
    val name = SavedTextField(viewModelScope, savedStateHandle, KEY_NAME) {
        _uiState.update { it.copy(nameError = null) }
    }

    private var loadJob: Job? = null

    /** The last [ServiceRepository.bouquetEdits] entry of this bouquet the list accounts for. */
    private var seenEdit = services.bouquetEdits.value[_uiState.value.bouquetRef]

    /** The receiver the bouquet belongs to. */
    private val receiver = editor.currentReceiver()

    init {
        viewModelScope.launch { editor.receiver.collect { receiverChanged() } }
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
                if (!blocked && _uiState.value.content is BouquetContentList.Failed) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            services.bouquetEdits.map { it[_uiState.value.bouquetRef] }.distinctUntilChanged()
                .collect { edit ->
                    // Own edits note theirs when they finish, and run while pending.
                    if (edit != seenEdit && !_uiState.value.pending) {
                        seenEdit = edit
                        reload()
                    }
                }
        }
        reload()
    }

    fun reload() {
        if (_uiState.value.pending || _uiState.value.closed) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            if (it.content is BouquetContentList.Ready) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = BouquetContentList.Loading)
            }
        }
        loadJob = viewModelScope.launch {
            val content = fetch()
            _uiState.update { it.copy(content = content, refreshing = false, menu = null) }
        }
    }

    fun onItemMenu(row: BouquetContentRow) {
        val rows = rows() ?: return
        val index = rows.indexOf(row)
        if (index < 0) {
            return
        }
        val actions = BouquetEntryAction.entries.filter {
            when (it) {
                BouquetEntryAction.Rename -> row.entry.kind != BouquetEntryKind.Alternative
                BouquetEntryAction.MoveUp -> index > 0
                BouquetEntryAction.MoveDown -> index < rows.lastIndex
                else -> true
            }
        }
        _uiState.update { it.copy(menu = RowMenuState(row.key, actions)) }
    }

    fun onMenuDismiss() {
        _uiState.update { it.copy(menu = null) }
    }

    fun onMenuAction(row: BouquetContentRow, action: BouquetEntryAction) {
        val index = rows()?.indexOf(row) ?: return
        if (index < 0) {
            return
        }
        when (action) {
            BouquetEntryAction.Rename -> openRename(row)
            BouquetEntryAction.InsertMarker -> openAddMarker(row)
            BouquetEntryAction.MoveUp -> move(row.key, index - 1)
            BouquetEntryAction.MoveDown -> move(row.key, index + 1)
            BouquetEntryAction.Remove -> openDialog(BouquetContentDialog.Remove(row))
        }
    }

    fun openRename(row: BouquetContentRow) {
        if (row.entry.kind == BouquetEntryKind.Alternative) {
            return
        }
        name.set(row.entry.name)
        openDialog(BouquetContentDialog.Rename(row))
    }

    /** Opens the marker dialog; the marker goes before [before], or at the end. */
    fun openAddMarker(before: BouquetContentRow? = null) {
        name.set("")
        openDialog(BouquetContentDialog.AddMarker(before))
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null, nameError = null) }
    }

    /**
     * Renames the dialog's entry. The box replaces it, so its reference changes; the entry
     * after it keeps the position, and the list loads again.
     */
    fun confirmRename() {
        val state = _uiState.value
        val row = (state.dialog as? BouquetContentDialog.Rename)?.row ?: return
        val rows = rows() ?: return
        val typed = acceptedName() ?: return
        dismissDialog()
        val index = rows.indexOf(row)
        if (!state.editable || index < 0 || typed == row.entry.name) {
            return
        }
        val before = rows.getOrNull(index + 1)?.entry?.reference.orEmpty()
        edit(reloadAfter = true) {
            editor.renameService(state.bouquetRef, row.entry.reference, before, typed)
        }
    }

    fun confirmAddMarker() {
        val state = _uiState.value
        val dialog = state.dialog as? BouquetContentDialog.AddMarker ?: return
        val typed = acceptedName() ?: return
        dismissDialog()
        if (!state.editable) {
            return
        }
        val before = dialog.before?.entry?.reference.orEmpty()
        edit(reloadAfter = true) { editor.addMarker(state.bouquetRef, typed, before) }
    }

    fun confirmRemove() {
        val state = _uiState.value
        val row = (state.dialog as? BouquetContentDialog.Remove)?.row ?: return
        dismissDialog()
        if (!state.editable) {
            return
        }
        edit(reloadAfter = rows().hasTwice(row.entry.reference), shown = { rows -> rows - row }) {
            editor.removeService(state.bouquetRef, row.entry.reference)
        }
    }

    /** Moves the row [key] to the 0-based [position]; markers count. */
    fun move(key: Int, position: Int) {
        val state = _uiState.value
        val rows = rows() ?: return
        val from = rows.indexOfFirst { it.key == key }
        if (!state.editable || from < 0 || position !in rows.indices || position == from) {
            return
        }
        val row = rows[from]
        edit(
            reloadAfter = rows.hasTwice(row.entry.reference),
            shown = { list -> list.toMutableList().apply { add(position, removeAt(from)) } }
        ) {
            editor.moveService(state.mode, state.bouquetRef, row.entry.reference, position)
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** The trimmed dialog text, or null after flagging a blank one. */
    private fun acceptedName(): String? {
        val typed = name.text.trim()
        if (typed.isEmpty()) {
            _uiState.update { it.copy(nameError = UiText.Resource(R.string.bouquet_name_empty)) }
            return null
        }
        return typed
    }

    /** Closes the list when the active profile's receiver is no longer the bouquet's. */
    private fun receiverChanged(): Boolean {
        if (editor.currentReceiver() == receiver) {
            return false
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(closed = true, pending = false, refreshing = false, menu = null, dialog = null)
        }
        return true
    }

    private fun openDialog(dialog: BouquetContentDialog) {
        if (!_uiState.value.editable) {
            return
        }
        _uiState.update { it.copy(dialog = dialog, nameError = null, menu = null) }
    }

    /**
     * Runs [call] as the one pending edit. [shown] is the list to show meanwhile. A failed
     * edit, or one with [reloadAfter], loads the list again before the next edit.
     */
    private fun edit(
        reloadAfter: Boolean,
        shown: ((List<BouquetContentRow>) -> List<BouquetContentRow>)? = null,
        call: suspend () -> BouquetEditResult
    ) {
        if (receiverChanged() || !_uiState.value.editable) {
            return
        }
        _uiState.update { state ->
            val content = state.content
            state.copy(
                pending = true,
                refreshing = false,
                menu = null,
                content = if (shown != null && content is BouquetContentList.Ready) {
                    BouquetContentList.Ready(shown(content.rows))
                } else {
                    content
                }
            )
        }
        loadJob = viewModelScope.launch {
            val result = call()
            seenEdit = services.bouquetEdits.value[_uiState.value.bouquetRef]
            val message = result.userMessage()
            val content = if (reloadAfter || !result.succeeded) fetch() else null
            _uiState.update {
                it.copy(
                    pending = false,
                    content = content ?: it.content,
                    userMessage = message ?: it.userMessage
                )
            }
        }
    }

    private suspend fun fetch(): BouquetContentList {
        val response = editor.entries(_uiState.value.bouquetRef)
        val entries = response.value
            ?: return BouquetContentList.Failed(response.error.contentErrorText())
        return BouquetContentList.Ready(
            entries.mapIndexed { index, entry -> BouquetContentRow(index, entry) }
        )
    }

    private fun rows(): List<BouquetContentRow>? =
        (_uiState.value.content as? BouquetContentList.Ready)?.rows

    /**
     * Whether more than one row has [ref], names aside: the box compares references without
     * the name and acts on the first match, which need not be the row on screen.
     */
    private fun List<BouquetContentRow>?.hasTwice(ref: String): Boolean {
        if (ref.isEmpty()) {
            return false
        }
        val key = presenceKey(ref)
        return orEmpty().count { presenceKey(it.entry.reference) == key } > 1
    }

    private companion object {
        const val KEY_NAME = "bouquet_content_name"
    }
}
