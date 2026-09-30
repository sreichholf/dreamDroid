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
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

enum class BouquetRowAction(override val label: Int) : RowMenuAction {
    Rename(R.string.rename),
    MoveUp(R.string.move_up),
    MoveDown(R.string.move_down),
    Remove(R.string.remove)
}

/** What the list area shows. */
sealed interface BouquetListContent {
    data object Loading : BouquetListContent

    /** The receiver does not list the WebBouquetEditor plugin. */
    data object NotInstalled : BouquetListContent

    data class Failed(val message: UiText) : BouquetListContent

    data class Ready(val bouquets: List<BouquetEntry>) : BouquetListContent
}

sealed interface BouquetListDialog {
    data object Add : BouquetListDialog

    data class Rename(val bouquet: BouquetEntry) : BouquetListDialog

    data class Remove(val bouquet: BouquetEntry) : BouquetListDialog
}

/**
 * The bouquet index of [mode]. [blocked] mirrors the session's `blocksMutations`; [pending]
 * is true while an edit (and the reload after it) runs. [nameError] rejects the text of the
 * open add or rename dialog.
 */
data class BouquetListUiState(
    val mode: BouquetMode = BouquetMode.Tv,
    val content: BouquetListContent = BouquetListContent.Loading,
    val refreshing: Boolean = false,
    val blocked: Boolean = false,
    val pending: Boolean = false,
    val menu: RowMenuState<BouquetRowAction>? = null,
    val dialog: BouquetListDialog? = null,
    val nameError: UiText? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.bouquets)

    /** Whether the list takes an edit now. */
    val editable: Boolean
        get() = content is BouquetListContent.Ready && !blocked && !pending
}

/**
 * The receiver's TV or radio bouquets through the WebBouquetEditor plugin. Creating it starts
 * an editor session: the first edit backs up the box. One edit runs at a time. Moves and
 * removals show at once; a failed edit loads the list again.
 */
@HiltViewModel
class BouquetListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val editor: BouquetEditorRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BouquetListUiState(
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { saved -> BouquetMode.entries.firstOrNull { it.name == saved } }
                ?: BouquetMode.Tv,
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<BouquetListUiState> = _uiState.asStateFlow()

    /** The name in the add or rename dialog. */
    val name = SavedTextField(viewModelScope, savedStateHandle, KEY_NAME) {
        _uiState.update { it.copy(nameError = null) }
    }

    private var available = false
    private var loadJob: Job? = null

    init {
        editor.resetBackup()
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
                if (!blocked && _uiState.value.content is BouquetListContent.Failed) {
                    reload()
                }
            }
        }
        reload()
    }

    /** Lists the bouquets again; checks for the plugin first until it was found. */
    fun reload() {
        if (_uiState.value.pending) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            if (it.content is BouquetListContent.Ready) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = BouquetListContent.Loading)
            }
        }
        val mode = _uiState.value.mode
        loadJob = viewModelScope.launch {
            val content = fetch(mode)
            _uiState.update { it.copy(content = content, refreshing = false, menu = null) }
        }
    }

    fun setMode(mode: BouquetMode) {
        val state = _uiState.value
        if (state.mode == mode || state.pending) {
            return
        }
        savedStateHandle[KEY_MODE] = mode.name
        _uiState.update {
            it.copy(mode = mode, content = BouquetListContent.Loading, refreshing = false)
        }
        reload()
    }

    fun onItemMenu(bouquet: BouquetEntry) {
        val bouquets = bouquets() ?: return
        val index = bouquets.indexOf(bouquet)
        if (index < 0) {
            return
        }
        val actions = BouquetRowAction.entries.filter {
            when (it) {
                BouquetRowAction.MoveUp -> index > 0
                BouquetRowAction.MoveDown -> index < bouquets.lastIndex
                else -> true
            }
        }
        _uiState.update { it.copy(menu = RowMenuState(bouquet.reference, actions)) }
    }

    fun onMenuDismiss() {
        _uiState.update { it.copy(menu = null) }
    }

    fun onMenuAction(bouquet: BouquetEntry, action: BouquetRowAction) {
        val index = bouquets()?.indexOf(bouquet) ?: return
        if (index < 0) {
            return
        }
        when (action) {
            BouquetRowAction.Rename -> openRename(bouquet)
            BouquetRowAction.MoveUp -> move(bouquet.reference, index - 1)
            BouquetRowAction.MoveDown -> move(bouquet.reference, index + 1)
            BouquetRowAction.Remove -> openDialog(BouquetListDialog.Remove(bouquet))
        }
    }

    fun openAdd() {
        name.set("")
        openDialog(BouquetListDialog.Add)
    }

    fun openRename(bouquet: BouquetEntry) {
        name.set(bouquet.name)
        openDialog(BouquetListDialog.Rename(bouquet))
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null, nameError = null) }
    }

    /** Adds the typed bouquet unless the name is blank or the index has it already. */
    fun confirmAdd() {
        val state = _uiState.value
        val bouquets = bouquets() ?: return
        if (!state.editable) {
            return
        }
        val typed = name.text.trim()
        val error = when {
            typed.isEmpty() -> R.string.bouquet_name_empty

            bouquets.any { it.name == typed + state.mode.nameSuffix } ->
                R.string.bouquet_name_exists

            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(nameError = UiText.Resource(error)) }
            return
        }
        dismissDialog()
        edit(reloadAfter = true) { editor.addBouquet(state.mode, typed) }
    }

    fun confirmRename() {
        val state = _uiState.value
        val bouquet = (state.dialog as? BouquetListDialog.Rename)?.bouquet ?: return
        val bouquets = bouquets() ?: return
        if (!state.editable) {
            return
        }
        val typed = name.text.trim()
        val error = when {
            typed.isEmpty() -> R.string.bouquet_name_empty
            bouquets.any { it != bouquet && it.name == typed } -> R.string.bouquet_name_exists
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(nameError = UiText.Resource(error)) }
            return
        }
        dismissDialog()
        if (typed == bouquet.name) {
            return
        }
        edit(reloadAfter = true) { editor.renameBouquet(state.mode, bouquet.reference, typed) }
    }

    fun confirmRemove() {
        val state = _uiState.value
        val bouquet = (state.dialog as? BouquetListDialog.Remove)?.bouquet ?: return
        dismissDialog()
        if (!state.editable) {
            return
        }
        edit(reloadAfter = false, shown = { list -> list - bouquet }) {
            editor.removeBouquet(state.mode, bouquet.reference)
        }
    }

    /** Moves [ref] to the 0-based [position] of the index. */
    fun move(ref: String, position: Int) {
        val state = _uiState.value
        val bouquets = bouquets() ?: return
        val from = bouquets.indexOfFirst { it.reference == ref }
        if (!state.editable || from < 0 || position !in bouquets.indices || position == from) {
            return
        }
        edit(
            reloadAfter = false,
            shown = { list -> list.toMutableList().apply { add(position, removeAt(from)) } }
        ) {
            editor.moveBouquet(state.mode, ref, position)
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun openDialog(dialog: BouquetListDialog) {
        if (!_uiState.value.editable) {
            return
        }
        _uiState.update { it.copy(dialog = dialog, nameError = null, menu = null) }
    }

    /**
     * Runs [call] as the one pending edit. [shown] is the list to show meanwhile. A failed
     * edit, or one with [reloadAfter], lists the bouquets again before the next edit.
     */
    private fun edit(
        reloadAfter: Boolean,
        shown: ((List<BouquetEntry>) -> List<BouquetEntry>)? = null,
        call: suspend () -> BouquetEditResult
    ) {
        loadJob?.cancel()
        val mode = _uiState.value.mode
        _uiState.update { state ->
            val content = state.content
            state.copy(
                pending = true,
                refreshing = false,
                menu = null,
                content = if (shown != null && content is BouquetListContent.Ready) {
                    BouquetListContent.Ready(shown(content.bouquets))
                } else {
                    content
                }
            )
        }
        loadJob = viewModelScope.launch {
            val result = call()
            val message = result.userMessage()
            val content = if (reloadAfter || !result.succeeded) fetch(mode) else null
            _uiState.update {
                it.copy(
                    pending = false,
                    content = content ?: it.content,
                    userMessage = message ?: it.userMessage
                )
            }
        }
    }

    private suspend fun fetch(mode: BouquetMode): BouquetListContent {
        if (!available) {
            val check = editor.isAvailable()
            when (check.value) {
                null -> return BouquetListContent.Failed(check.error.contentErrorText())
                false -> return BouquetListContent.NotInstalled
                true -> available = true
            }
        }
        val response = editor.bouquets(mode)
        return response.value?.let { BouquetListContent.Ready(it) }
            ?: BouquetListContent.Failed(response.error.contentErrorText())
    }

    private fun bouquets(): List<BouquetEntry>? =
        (_uiState.value.content as? BouquetListContent.Ready)?.bouquets

    private companion object {
        const val KEY_MODE = "bouquet_list_mode"
        const val KEY_NAME = "bouquet_list_name"
    }
}

/** What the box appends to a new bouquet's name. */
private val BouquetMode.nameSuffix: String
    get() = when (this) {
        BouquetMode.Tv -> " (TV)"
        BouquetMode.Radio -> " (Radio)"
    }

/** A failed edit's reply; else the result of the backup this edit ran first, if any. */
private fun BouquetEditResult.userMessage(): UiText? {
    if (!succeeded) {
        return response.userMessageText()
    }
    val backup = backup ?: return null
    val file = backup.value?.stateText
    return if (backup.error == null && !file.isNullOrEmpty()) {
        UiText.Resource(R.string.bouquet_backup_saved, listOf(file))
    } else {
        UiText.Resource(R.string.bouquet_backup_failed)
    }
}
